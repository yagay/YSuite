package com.yagay.YSuite.xposed;

import android.util.Log;

import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Process-local hook multiplexer owned by YSuite.
 *
 * <p>Every plugin receives a proxy XposedInterface. Calls to hook()/hookClassInitializer() are
 * collected here. For any target executable YSuite installs exactly one physical libxposed hook and
 * runs plugin hookers as a logical interceptor chain. This prevents independently developed
 * features from installing competing physical hooks in the same target process.</p>
 */
final class SuiteHookRegistry {
    private static final String TAG = "YSuite.Hook";
    private static final String SHARED_HOOK_ID = "ysuite.shared";

    private final XposedModule host;
    private final ConcurrentHashMap<TargetKey, SharedHook> targets = new ConcurrentHashMap<>();
    private final AtomicLong registrationOrder = new AtomicLong();

    SuiteHookRegistry(XposedModule host) {
        this.host = Objects.requireNonNull(host);
    }

    XposedInterface.HookBuilder hookBuilder(String pluginId, Executable origin) {
        return new LogicalHookBuilder(
                this,
                Objects.requireNonNull(pluginId),
                TargetKey.executable(Objects.requireNonNull(origin)));
    }

    XposedInterface.HookBuilder classInitializerBuilder(String pluginId, Class<?> origin) {
        return new LogicalHookBuilder(
                this,
                Objects.requireNonNull(pluginId),
                TargetKey.classInitializer(Objects.requireNonNull(origin)));
    }

    private XposedInterface.HookHandle register(
            String pluginId,
            TargetKey target,
            int priority,
            XposedInterface.ExceptionMode mode,
            String id,
            XposedInterface.Hooker hooker) {
        SharedHook shared = targets.computeIfAbsent(target, key -> new SharedHook(key));
        return shared.register(
                pluginId,
                priority,
                mode == null ? XposedInterface.ExceptionMode.DEFAULT : mode,
                id,
                Objects.requireNonNull(hooker));
    }

    private final class SharedHook {
        private final TargetKey target;
        private final ArrayList<LogicalHook> logicalHooks = new ArrayList<>();
        private XposedInterface.HookHandle physicalHandle;

        SharedHook(TargetKey target) {
            this.target = target;
        }

        synchronized XposedInterface.HookHandle register(
                String pluginId,
                int priority,
                XposedInterface.ExceptionMode mode,
                String id,
                XposedInterface.Hooker hooker) {
            LogicalHook logical = null;
            if (id != null) {
                for (LogicalHook existing : logicalHooks) {
                    if (existing.active
                            && pluginId.equals(existing.pluginId)
                            && id.equals(existing.id)) {
                        logical = existing;
                        break;
                    }
                }
            }

            if (logical == null) {
                logical = new LogicalHook(
                        this,
                        pluginId,
                        priority,
                        mode,
                        id,
                        hooker,
                        registrationOrder.incrementAndGet());
                logicalHooks.add(logical);
            } else {
                logical.priority = priority;
                logical.mode = mode;
                logical.hooker = hooker;
            }

            try {
                ensurePhysicalHook();
            } catch (Throwable error) {
                if (logical.id == null) {
                    logical.active = false;
                    logicalHooks.remove(logical);
                }
                if (error instanceof RuntimeException runtime) throw runtime;
                if (error instanceof Error fatal) throw fatal;
                throw new IllegalStateException(error);
            }

            logSharedOwnersIfNeeded();
            return new LogicalHookHandle(logical);
        }

        private void ensurePhysicalHook() {
            if (physicalHandle != null) return;

            XposedInterface.HookBuilder builder = target.classInitializer
                    ? host.hookClassInitializer(target.initializerClass)
                    : host.hook(target.executable);

            // Internal plugin ordering is handled by YSuite. Keep one framework-visible hook with a
            // stable id and protective boundary so a plugin failure never destabilizes the target.
            physicalHandle = builder
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .setId(SHARED_HOOK_ID)
                    .intercept(new SharedDispatcher(this));

            host.log(
                    Log.INFO,
                    TAG,
                    "physical hook installed target=" + target.label());
        }

        private void logSharedOwnersIfNeeded() {
            ArrayList<String> owners = new ArrayList<>();
            for (LogicalHook hook : logicalHooks) {
                if (hook.active && !owners.contains(hook.pluginId)) owners.add(hook.pluginId);
            }
            if (owners.size() > 1) {
                host.log(
                        Log.INFO,
                        TAG,
                        "shared target=" + target.label() + " owners=" + owners);
            }
        }

        synchronized void remove(LogicalHook logical) {
            if (!logical.active) return;
            logical.active = false;
            logicalHooks.remove(logical);
            if (!logicalHooks.isEmpty()) return;

            XposedInterface.HookHandle handle = physicalHandle;
            physicalHandle = null;
            targets.remove(target, this);
            if (handle != null) {
                try {
                    handle.unhook();
                } catch (Throwable error) {
                    host.log(Log.WARN, TAG, "physical unhook failed target=" + target.label(), error);
                }
            }
        }

        synchronized void replace(LogicalHook logical, XposedInterface.Hooker hooker) {
            if (!logical.active || !logicalHooks.contains(logical)) {
                throw new IllegalStateException("Logical hook is no longer active");
            }
            logical.hooker = Objects.requireNonNull(hooker);
        }

        private List<LogicalHook> snapshot() {
            ArrayList<LogicalHook> out;
            synchronized (this) {
                out = new ArrayList<>(logicalHooks.size());
                for (LogicalHook hook : logicalHooks) {
                    if (hook.active) out.add(hook);
                }
            }
            out.sort(new Comparator<>() {
                @Override
                public int compare(LogicalHook left, LogicalHook right) {
                    int byPriority = Integer.compare(right.priority, left.priority);
                    if (byPriority != 0) return byPriority;
                    return Long.compare(left.order, right.order);
                }
            });
            return out;
        }

        Object dispatch(XposedInterface.Chain physicalChain) throws Throwable {
            List<LogicalHook> snapshot = snapshot();
            if (snapshot.isEmpty()) return physicalChain.proceed();

            Object[] args = physicalChain.getArgs().toArray(new Object[0]);
            Dispatch dispatch = new Dispatch(physicalChain, snapshot);
            return dispatch.invoke(0, physicalChain.getThisObject(), args);
        }
    }

    private static final class LogicalHook {
        final SharedHook owner;
        final String pluginId;
        final String id;
        final long order;

        volatile int priority;
        volatile XposedInterface.ExceptionMode mode;
        volatile XposedInterface.Hooker hooker;
        volatile boolean active = true;

        LogicalHook(
                SharedHook owner,
                String pluginId,
                int priority,
                XposedInterface.ExceptionMode mode,
                String id,
                XposedInterface.Hooker hooker,
                long order) {
            this.owner = owner;
            this.pluginId = pluginId;
            this.priority = priority;
            this.mode = mode;
            this.id = id;
            this.hooker = hooker;
            this.order = order;
        }
    }

    private final class Dispatch {
        private final XposedInterface.Chain physicalChain;
        private final List<LogicalHook> snapshot;

        Dispatch(XposedInterface.Chain physicalChain, List<LogicalHook> snapshot) {
            this.physicalChain = physicalChain;
            this.snapshot = snapshot;
        }

        Object invoke(int index, Object thisObject, Object[] args) throws Throwable {
            if (index >= snapshot.size()) {
                Executable executable = physicalChain.getExecutable();
                if (Modifier.isStatic(executable.getModifiers())
                        || thisObject == physicalChain.getThisObject()) {
                    return physicalChain.proceed(args);
                }
                return physicalChain.proceedWith(thisObject, args);
            }

            LogicalHook logical = snapshot.get(index);
            if (!logical.active) return invoke(index + 1, thisObject, args);

            LogicalChain chain = new LogicalChain(this, index + 1, thisObject, args);
            try {
                return logical.hooker.intercept(chain);
            } catch (Throwable error) {
                if (logical.mode == XposedInterface.ExceptionMode.PASSTHROUGH) throw error;

                host.log(
                        Log.ERROR,
                        TAG,
                        "plugin hook failed; plugin=" + logical.pluginId
                                + " target=" + physicalChain.getExecutable(),
                        error);

                // Match libxposed PROTECTIVE semantics. Exceptions thrown by downstream proceed()
                // still propagate. A plugin exception after a successful proceed keeps that result.
                if (chain.proceeded) {
                    if (chain.proceedFailure != null) throw chain.proceedFailure;
                    return chain.proceedResult;
                }
                return invoke(index + 1, thisObject, args);
            }
        }
    }

    private static final class LogicalChain implements XposedInterface.Chain {
        private final Dispatch dispatch;
        private final int nextIndex;
        private final Object thisObject;
        private final Object[] args;

        private boolean proceeded;
        private Object proceedResult;
        private Throwable proceedFailure;

        LogicalChain(Dispatch dispatch, int nextIndex, Object thisObject, Object[] args) {
            this.dispatch = dispatch;
            this.nextIndex = nextIndex;
            this.thisObject = thisObject;
            this.args = args == null ? new Object[0] : args.clone();
        }

        @Override
        public Executable getExecutable() {
            return dispatch.physicalChain.getExecutable();
        }

        @Override
        public Object getThisObject() {
            return thisObject;
        }

        @Override
        public List<Object> getArgs() {
            Object[] copy = args.clone();
            return Collections.unmodifiableList(Arrays.asList(copy));
        }

        @Override
        public Object getArg(int index) {
            return args[index];
        }

        @Override
        public Object proceed() throws Throwable {
            return proceedInternal(thisObject, args);
        }

        @Override
        public Object proceed(Object[] newArgs) throws Throwable {
            return proceedInternal(thisObject, newArgs);
        }

        @Override
        public Object proceedWith(Object newThisObject) throws Throwable {
            return proceedInternal(newThisObject, args);
        }

        @Override
        public Object proceedWith(Object newThisObject, Object[] newArgs) throws Throwable {
            return proceedInternal(newThisObject, newArgs);
        }

        private Object proceedInternal(Object nextThisObject, Object[] nextArgs) throws Throwable {
            proceeded = true;
            try {
                Object result = dispatch.invoke(
                        nextIndex,
                        nextThisObject,
                        nextArgs == null ? new Object[0] : nextArgs.clone());
                proceedResult = result;
                proceedFailure = null;
                return result;
            } catch (Throwable error) {
                proceedFailure = error;
                throw error;
            }
        }
    }

    private static final class SharedDispatcher implements XposedInterface.Hooker {
        private final SharedHook shared;

        SharedDispatcher(SharedHook shared) {
            this.shared = shared;
        }

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            return shared.dispatch(chain);
        }
    }

    private static final class LogicalHookBuilder implements XposedInterface.HookBuilder {
        private final SuiteHookRegistry registry;
        private final String pluginId;
        private final TargetKey target;

        private int priority = XposedInterface.PRIORITY_DEFAULT;
        private XposedInterface.ExceptionMode mode = XposedInterface.ExceptionMode.DEFAULT;
        private String id;

        LogicalHookBuilder(SuiteHookRegistry registry, String pluginId, TargetKey target) {
            this.registry = registry;
            this.pluginId = pluginId;
            this.target = target;
        }

        @Override
        public XposedInterface.HookBuilder setPriority(int priority) {
            this.priority = priority;
            return this;
        }

        @Override
        public XposedInterface.HookBuilder setExceptionMode(XposedInterface.ExceptionMode mode) {
            this.mode = Objects.requireNonNull(mode);
            return this;
        }

        @Override
        public XposedInterface.HookBuilder setId(String id) {
            this.id = id;
            return this;
        }

        @Override
        public XposedInterface.HookHandle intercept(XposedInterface.Hooker hooker) {
            return registry.register(pluginId, target, priority, mode, id, hooker);
        }
    }

    private static final class LogicalHookHandle implements XposedInterface.HookHandle {
        private final LogicalHook logical;

        LogicalHookHandle(LogicalHook logical) {
            this.logical = logical;
        }

        @Override
        public Executable getExecutable() {
            if (logical.owner.target.executable != null) return logical.owner.target.executable;
            XposedInterface.HookHandle physical = logical.owner.physicalHandle;
            if (physical == null) throw new IllegalStateException("Physical hook is not active");
            return physical.getExecutable();
        }

        @Override
        public void unhook() {
            logical.owner.remove(logical);
        }

        @Override
        public String getId() {
            return logical.id;
        }

        @Override
        public XposedInterface.HookHandle replaceHook(XposedInterface.Hooker hooker) {
            logical.owner.replace(logical, hooker);
            return this;
        }
    }

    private record TargetKey(
            Executable executable,
            Class<?> initializerClass,
            boolean classInitializer) {
        static TargetKey executable(Executable executable) {
            return new TargetKey(executable, null, false);
        }

        static TargetKey classInitializer(Class<?> origin) {
            return new TargetKey(null, origin, true);
        }

        String label() {
            return classInitializer
                    ? initializerClass.getName() + "#<clinit>"
                    : executable.toString();
        }
    }
}
