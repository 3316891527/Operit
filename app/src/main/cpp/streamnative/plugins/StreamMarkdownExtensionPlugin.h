#pragma once
#include "StreamPlugin.h"

namespace streamnative {

/** 两字符转义是完整词元，不等待下一个字符，跨分片时仍保留状态。 */
class StreamMarkdownEscapePlugin final : public StreamPlugin {
    PluginState state_ = PluginState::IDLE;
public:
    PluginState state() const override { return state_; }
    bool initPlugin() override { reset(); return true; }
    void reset() override { state_ = PluginState::IDLE; }
    bool processChar(char16_t c, bool) override {
        if (state_ == PluginState::TRYING) {
            const bool punctuation = (c >= u'!' && c <= u'/') || (c >= u':' && c <= u'@') ||
                (c >= u'[' && c <= u'`') || (c >= u'{' && c <= u'~') || c == u'\n';
            state_ = punctuation && c != u'(' && c != u'[' ? PluginState::MATCHED : PluginState::IDLE;
        } else state_ = c == u'\\' ? PluginState::TRYING : PluginState::IDLE;
        return true;
    }
};

/** 保留标记供绘制层检查闭合，未完成的输入不吞掉原始字符。 */
class StreamMarkdownDelimitedPlugin final : public StreamPlugin {
    char16_t marker_;
    int length_;
    int opening_ = 0;
    int closing_ = 0;
    PluginState state_ = PluginState::IDLE;
public:
    StreamMarkdownDelimitedPlugin(char16_t marker, int length) : marker_(marker), length_(length) {}
    PluginState state() const override { return state_; }
    bool initPlugin() override { reset(); return true; }
    void reset() override { state_ = PluginState::IDLE; opening_ = closing_ = 0; }
    bool processChar(char16_t c, bool) override {
        if (state_ == PluginState::PROCESSING) {
            if (c == u'\n') { reset(); return true; }
            closing_ = c == marker_ ? closing_ + 1 : 0;
            if (closing_ == length_) reset();
            return true;
        }
        if (state_ == PluginState::TRYING) {
            if (opening_ < length_ && c == marker_) {
                if (++opening_ == length_ && length_ > 1) state_ = PluginState::PROCESSING;
            } else if (length_ == 1 && c != marker_ && c != u'\n' && c != u' ' && c != u'\t') {
                state_ = PluginState::PROCESSING;
            } else reset();
            return true;
        }
        if (c == marker_) { opening_ = 1; state_ = PluginState::TRYING; }
        return true;
    }
};

/** 列表前缀连同缩进保留，序号、任务状态和子块由同一份源码解析。 */
class StreamMarkdownListItemPlugin final : public StreamPlugin {
    bool ordered_;
    int phase_ = 0;
    int digits_ = 0;
    PluginState state_ = PluginState::IDLE;
public:
    explicit StreamMarkdownListItemPlugin(bool ordered) : ordered_(ordered) {}
    PluginState state() const override { return state_; }
    bool initPlugin() override { reset(); return true; }
    void reset() override { state_ = PluginState::IDLE; phase_ = digits_ = 0; }
    bool processChar(char16_t c, bool atStartOfLine) override {
        if (state_ == PluginState::PROCESSING) {
            if (c == u'\n') reset();
            return true;
        }
        if (atStartOfLine) { reset(); state_ = PluginState::TRYING; }
        if (state_ != PluginState::TRYING) return true;
        if (phase_ == 0) {
            if (c == u' ' || c == u'\t') return true;
            if (ordered_ && c >= u'0' && c <= u'9') { phase_ = 1; digits_ = 1; return true; }
            if (!ordered_ && (c == u'-' || c == u'+' || c == u'*')) { phase_ = 2; return true; }
        } else if (phase_ == 1) {
            if (c >= u'0' && c <= u'9' && ++digits_ <= 9) return true;
            if (c == u'.' || c == u')') { phase_ = 2; return true; }
        } else if (phase_ == 2 && (c == u' ' || c == u'\t')) {
            state_ = PluginState::PROCESSING;
            return true;
        }
        reset();
        return true;
    }
};

/** 地址中的括号、转义和可选标题都属于同一个链接，不在首个右括号提前结束。 */
class StreamMarkdownTargetPlugin final : public StreamPlugin {
    bool image_;
    int phase_ = 0;
    int labelDepth_ = 0;
    int targetDepth_ = 0;
    bool escaped_ = false;
    bool angle_ = false;
    char16_t quote_ = 0;
    PluginState state_ = PluginState::IDLE;
public:
    explicit StreamMarkdownTargetPlugin(bool image) : image_(image) {}
    PluginState state() const override { return state_; }
    bool initPlugin() override { reset(); return true; }
    void reset() override {
        state_ = PluginState::IDLE; phase_ = labelDepth_ = targetDepth_ = 0;
        escaped_ = angle_ = false; quote_ = 0;
    }
    bool processChar(char16_t c, bool) override {
        if (c == u'\n') { reset(); return true; }
        if (state_ == PluginState::IDLE) {
            if (c == (image_ ? u'!' : u'[')) {
                state_ = PluginState::TRYING; phase_ = image_ ? 1 : 2; labelDepth_ = image_ ? 0 : 1;
            }
            return true;
        }
        if (escaped_) { escaped_ = false; return true; }
        if (c == u'\\') { escaped_ = true; return true; }
        if (phase_ == 1) {
            if (c == u'[') { phase_ = 2; labelDepth_ = 1; } else reset();
            return true;
        }
        if (phase_ == 2) {
            if (c == u'[') labelDepth_++;
            if (c == u']' && --labelDepth_ == 0) phase_ = 3;
            return true;
        }
        if (phase_ == 3) {
            if (c == u'(') { phase_ = 4; targetDepth_ = 1; state_ = PluginState::PROCESSING; } else reset();
            return true;
        }
        if (quote_ != 0) { if (c == quote_) quote_ = 0; return true; }
        if (angle_) { if (c == u'>') angle_ = false; return true; }
        if (c == u'<' && targetDepth_ == 1) { angle_ = true; return true; }
        if ((c == u'"' || c == u'\'') && targetDepth_ == 1) { quote_ = c; return true; }
        if (c == u'(') targetDepth_++;
        if (c == u')' && --targetDepth_ == 0) reset();
        return true;
    }
};

} // namespace streamnative