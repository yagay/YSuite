package com.yagay.ysuite.feature.yfiles.plugin;

interface IYFilesPlugin {
    int protocolVersion();
    String descriptorJson();
    String call(String requestJson);
}
