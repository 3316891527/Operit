package com.ai.assistance.operit.ui.features.conversation.workspace.editor.language

import android.graphics.Color

/**
 * 语言支持接口，定义了语言高亮相关的基础能力
 */
interface LanguageSupport {
    /**
     * 获取语言名称
     */
    fun getName(): String
    
    /**
     * 获取语言的关键字集合
     */
    fun getKeywords(): Set<String>
    
    /**
     * 获取语言的内置函数集合
     */
    fun getBuiltInFunctions(): Set<String> = emptySet()
    
    /**
     * 获取语言的内置类型集合
     */
    fun getBuiltInTypes(): Set<String> = emptySet()

    /**
     * 获取语言的内置变量集合
     */
    fun getBuiltInVariables(): Set<String> = emptySet()

    /**
     * 获取语言的注释起始标记
     * 如 // 或
     */
    fun getCommentStart(): List<String>
    
    /**
     * 获取语言的多行注释结束标记
     * 如
     */
    fun getMultiLineCommentEnd(): String?
    
    /**
     * 检查是否是字符串开始
     */
    fun isStringDelimiter(char: Char): Boolean
    
    /**
     * 获取语言的字符串转义字符
     */
    fun getStringEscapeChar(): Char
    
    /**
     * 获取语言的文件扩展名
     */
    fun getFileExtensions(): List<String>

    /**
     * 语法高亮分类颜色，以深色主题为基准，由视图映射到当前主题
     */
    companion object {
        // 分类颜色保持各自独立，字符串使用经典绿色；浅深主题由编辑器统一映射。
        val KEYWORD_COLOR = Color.parseColor("#569CD6")      // Blue for keywords (if, else, return, etc.)
        val FUNCTION_COLOR = Color.parseColor("#DCDCAA")     // Yellow for functions and methods
        val STRING_COLOR = Color.parseColor("#6A8759")       // 字符串使用经典绿色
        val NUMBER_COLOR = Color.parseColor("#B5CEA8")       // Light green for numbers
        val COMMENT_COLOR = Color.parseColor("#9E9E9E")      // 灰色注释
        val TYPE_COLOR = Color.parseColor("#4EC9B0")         // Cyan for types, classes, interfaces
        val VARIABLE_COLOR = Color.parseColor("#9CDCFE")     // Light blue for variables and parameters
        val OPERATOR_COLOR = Color.parseColor("#D4D4D4")     // Light grey for operators
        val DEFAULT_COLOR = Color.parseColor("#D4D4D4")      // Light grey for default text
    }
} 
