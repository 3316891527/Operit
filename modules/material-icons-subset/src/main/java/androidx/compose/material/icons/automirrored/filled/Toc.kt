/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.material.icons.automirrored.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.AutoMirrored.Filled.Toc: ImageVector
    get() {
        if (_toc != null) {
            return _toc!!
        }
        _toc = materialIcon(name = "AutoMirrored.Filled.Toc", autoMirror = true) {
            materialPath {
                moveTo(3.0f, 9.0f)
                horizontalLineToRelative(14.0f)
                lineTo(17.0f, 7.0f)
                lineTo(3.0f, 7.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(3.0f, 13.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(-2.0f)
                lineTo(3.0f, 11.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(3.0f, 17.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(-2.0f)
                lineTo(3.0f, 15.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(19.0f, 17.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(19.0f, 7.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                lineTo(21.0f, 7.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(19.0f, 13.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
            }
        }
        return _toc!!
    }

private var _toc: ImageVector? = null
