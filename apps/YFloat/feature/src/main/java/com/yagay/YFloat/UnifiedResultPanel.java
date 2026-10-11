package com.yagay.YFloat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YOverlayUi;
import android.graphics.Rect;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * The one visual implementation for every YFloat result.
 *
 * This class owns only card content. Window sizing, outside-touch dismissal and centering belong to
 * UnifiedResultDialogFragment. The hierarchy is fixed for every mode: title, image slot, selectable
 * text slot, then OCR / Copy / Save / Close. The action row is therefore always measured after the
 * content and cannot be clipped by a manually calculated popup height.
 */
final class UnifiedResultPanel {
    private final Context context;
    private final FloatSettings settings;
    private final LinearLayout root;
    private final android.widget.TextView title;
    private final ImageView imageView;
    private final LinearLayout textPanel;
    private final TextSelectionSurface selection;
    private final TextActionMenuController textMenu;
    private final Button ocrButton;
    private final Button copyButton;
    private final Button saveButton;
    private final Button closeButton;
    private final int width;
    private final int titleHeight;
    private final int minimumActionsHeight;
    private final int maxPanelHeight;
    private final LinearLayout actions;
    private int contentBudget;

    private ResultSession session;
    private boolean ocrRunning;

    UnifiedResultPanel(Context context, ResultSession initial) {
        this.context = context;
        this.settings = new FloatSettings(context.getApplicationContext());
        this.textMenu = new TextActionMenuController(context);

        Rect usable = ResultUi.usableBounds(context);
        width = ResultUi.standardWidth(context, usable);
        maxPanelHeight = ResultUi.standardMaxHeight(context, usable);
        titleHeight = ResultUi.titleHeight(context);
        minimumActionsHeight = ResultUi.actionHeight(context);
        contentBudget = Math.max(1,
                maxPanelHeight - titleHeight - minimumActionsHeight - ResultUi.verticalPadding(context));

        root = ResultUi.box(context);

        title = ResultUi.heading(context, context.getString(R.string.yfloat_app_name));
        title.setMinHeight(titleHeight);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        imageView = new ImageView(context);
        imageView.setAdjustViewBounds(false);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setVisibility(View.GONE);
        root.addView(imageView, new LinearLayout.LayoutParams(-1, 0));

        textPanel = new LinearLayout(context);
        textPanel.setOrientation(LinearLayout.VERTICAL);
        int inset = com.yagay.yui.YOverlayUi.contentPaddingPx(context, "yfloat");
        textPanel.setPadding(0, inset / 2, 0, inset / 2);
        selection = new TextSelectionSurface(context);
        textPanel.addView(selection, new LinearLayout.LayoutParams(-1, 0, 1f));
        textPanel.setVisibility(View.GONE);
        root.addView(textPanel, new LinearLayout.LayoutParams(-1, 0));
        bindSelectionMenu();

        actions = ResultUi.actionRow(context);
        ocrButton = ResultUi.button(context, "OCR");
        copyButton = ResultUi.button(context, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_f5a24d2506d7));
        saveButton = ResultUi.button(context, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_82522e2a87bf));
        closeButton = ResultUi.button(context, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_59a26fdc46ce));
        YViewLayout.addAction(actions, ocrButton);
        YViewLayout.addAction(actions, copyButton);
        YViewLayout.addAction(actions, saveButton);
        YViewLayout.addAction(actions, closeButton);
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        copyButton.setOnClickListener(v -> copyAll());
        render(initial);
    }

    LinearLayout root() { return root; }
    int width() { return width; }
    TextSelectionSurface selection() { return selection; }
    ResultSession session() { return session; }

    void bindActions(Runnable onOcr, Runnable onSave, Runnable onClose) {
        ocrButton.setOnClickListener(v -> { if (onOcr != null) onOcr.run(); });
        saveButton.setOnClickListener(v -> { if (onSave != null) onSave.run(); });
        closeButton.setOnClickListener(v -> { if (onClose != null) onClose.run(); });
    }

    void render(ResultSession next) {
        if (next == null) return;
        session = next;
        selection.clearSelection();
        textMenu.dismiss();

        title.setText(next.title());
        updateActions();
        // Size the content budget from the actual label/action heights, not a fixed
        // action-bar height that would cut off multi-line or translated buttons.
        int availableWidth = Math.max(1, width -
                YOverlayUi.contentPaddingPx(context, "yfloat") * 2);
        int exactWidth = android.view.View.MeasureSpec.makeMeasureSpec(
                availableWidth, android.view.View.MeasureSpec.EXACTLY);
        int maxHeightSpec = android.view.View.MeasureSpec.makeMeasureSpec(
                maxPanelHeight, android.view.View.MeasureSpec.AT_MOST);
        actions.measure(exactWidth, maxHeightSpec);
        title.measure(exactWidth, maxHeightSpec);
        contentBudget = Math.max(1, maxPanelHeight -
                Math.max(titleHeight, title.getMeasuredHeight()) -
                Math.max(minimumActionsHeight, actions.getMeasuredHeight()) -
                ResultUi.verticalPadding(context));
        boolean showImage = next.showImage(settings);
        boolean showText = next.showText(settings);

        int imageHeight = 0;
        if (showImage) {
            imageView.setImageBitmap(next.image());
            imageView.setVisibility(View.VISIBLE);
            int cap;
            if (showText) {
                cap = contentBudget / 2;
            } else if (next.mode() == ResultSession.Mode.SCREENSHOT) {
                cap = contentBudget;
            } else if (next.mode() == ResultSession.Mode.VIEW_IMAGE) {
                cap = contentBudget;
            } else {
                cap = contentBudget;
            }
            imageHeight = ResultUi.imageHeight(context, next.image(), width, cap);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(-1, imageHeight));
            ImageShareUtils.attachClickMenu(context, imageView, next.image());
        } else {
            imageView.setOnClickListener(null);
            imageView.setOnLongClickListener(null);
            imageView.setClickable(false);
            imageView.setLongClickable(false);
            imageView.setImageDrawable(null);
            imageView.setVisibility(View.GONE);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(-1, 0));
        }

        int textHeight = 0;
        if (showText) {
            int remaining = Math.max(1, contentBudget - imageHeight);
            textHeight = Math.min(contentBudget, Math.max(1, remaining));
            textPanel.setVisibility(View.VISIBLE);
            textPanel.setLayoutParams(new LinearLayout.LayoutParams(-1, textHeight));
            selection.setText(next.displayText());
        } else {
            textPanel.setVisibility(View.GONE);
            textPanel.setLayoutParams(new LinearLayout.LayoutParams(-1, 0));
            selection.setText("");
        }

        root.requestLayout();

        DiagnosticLog.i(context, "RESULT_PANEL", "render mode=" + next.mode()
                + " origin=" + next.originMode()
                + " image=" + showImage + " imageH=" + imageHeight
                + " text=" + showText + " textH=" + textHeight
                + " width=" + width + " wrapContent=true intrinsicActions=true"
                + " buttons=ocr/copy/save/close");
    }

    void setOcrRunning(boolean running) {
        ocrRunning = running;
        updateActions();
    }

    void clearSelection() {
        selection.clearSelection();
        textMenu.dismiss();
    }

    private void updateActions() {
        ResultSession s = session;
        boolean canOcr = s != null && s.canOcr();
        boolean canCopy = s != null && s.canCopy();
        boolean canSave = s != null && s.canSave();

        ocrButton.setEnabled(canOcr && !ocrRunning);
        ocrButton.setText(ocrRunning ? com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_27662fcdbce1)
                : s != null && s.mode() == ResultSession.Mode.OCR ? com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_b579e97a6ef4) : "OCR");
        setEnabledVisual(ocrButton, canOcr && !ocrRunning);

        copyButton.setEnabled(canCopy);
        copyButton.setText(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_f5a24d2506d7));
        setEnabledVisual(copyButton, canCopy);

        saveButton.setEnabled(canSave);
        saveButton.setText(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_82522e2a87bf));
        setEnabledVisual(saveButton, canSave);

        closeButton.setEnabled(true);
        closeButton.setText(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_59a26fdc46ce));
        setEnabledVisual(closeButton, true);
    }

    private void setEnabledVisual(Button button, boolean enabled) {
        button.setVisibility(View.VISIBLE);
        button.setAlpha(enabled ? 1f : 0.42f);
    }

    private void bindSelectionMenu() {
        selection.setListener(new TextSelectionSurface.Listener() {
            @Override public void onSelectionStarted() {
                textMenu.dismiss();
            }

            @Override public void onSelectionChanging() {
                textMenu.dismiss();
            }

            @Override public void onSelectionFinished(SelectionSnapshot snapshot) {
                if (snapshot == null || snapshot.text().isBlank()) return;
                textMenu.show(snapshot, selection::selectAllText);
                Rect anchor = snapshot.screenBounds();
                DiagnosticLog.i(context, "RESULT_TEXT_MENU",
                        "show generation=" + snapshot.generation()
                                + " range=" + snapshot.start() + "-" + snapshot.end()
                                + " local=" + (snapshot.localBounds() == null
                                ? "none" : snapshot.localBounds().toShortString())
                                + " screen=" + (anchor == null ? "none" : anchor.toShortString())
                                + " controller=single");
            }
        });
    }

    private void copyAll() {
        if (session == null || !session.canCopy()) return;

        if (session.hasText()) {
            String value = selection.editor().getText().toString();
            if (value.isBlank()) value = session.displayText();
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("YFloat", value));
            Toast.makeText(context, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_2e84b8e9c556), Toast.LENGTH_SHORT).show();
            return;
        }

        if (session.hasImage()) {
            ImageShareUtils.copyToClipboard(context, session.image());
        }
    }
}
