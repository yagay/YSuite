package com.yagay.ysuite.feature.yfiles

import androidx.core.content.FileProvider

/** Avoid manifest-merger collision with the YSuite host's own FileProvider. */
class YFilesFileProvider : FileProvider()
