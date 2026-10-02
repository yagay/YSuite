package com.yagay.yfiles

import androidx.core.content.FileProvider

/**
 * Dedicated provider class so YFiles can coexist with other feature FileProviders
 * inside the merged YSuite manifest without provider-node collisions.
 */
class YFilesFileProvider : FileProvider()
