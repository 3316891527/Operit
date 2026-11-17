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

package androidx.compose.material.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Filled.TableRows: ImageVector
    get() {
        if (_tableRows != null) {
            return _tableRows!!
        }
        _tableRows = materialIcon(name = "Filled.TableRows") {
            materialPath {
                moveTo(21.0f, 8.0f)
                horizontalLineTo(3.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(18.0f)
                verticalLineTo(8.0f)
                close()
                moveTo(21.0f, 10.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(18.0f)
                verticalLineTo(10.0f)
                close()
                moveTo(21.0f, 16.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(18.0f)
                verticalLineTo(16.0f)
                close()
            }
        }
        return _tableRows!!
    }

private var _tableRows: ImageVector? = null
