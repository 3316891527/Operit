"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getTerminalSession = getTerminalSession;
exports.terminalExec = terminalExec;
async function getTerminalSession(sessionName) {
    // 会话名每次交给宿主解析，避免引擎复用时缓存指向其他名称或已经关闭的会话。
    const session = await Tools.System.terminal.create(sessionName || 'github_tools_session');
    return session.sessionId;
}
async function terminalExec(params) {
    const traceId = Date.now().toString(36) + '-' + Math.random().toString(36).slice(2);
    const started = Date.now();
    let stage = 'create';
    let sessionId;
    // 只记录阶段、关联 ID 和耗时，命令及输出不得进入诊断日志。
    const trace = (phase) => {
        const session = sessionId === undefined ? '' : ' session=' + sessionId;
        console.log('[github-terminal] trace=' + traceId + ' phase=' + phase + ' elapsedMs=' + (Date.now() - started) + session);
    };
    trace('begin');
    try {
        trace(stage + '.begin');
        sessionId = await getTerminalSession(params.session_name);
        trace(stage + '.end');
        stage = 'exec';
        trace('exec.begin');
        const result = await Tools.System.terminal.exec(sessionId, params.command);
        trace('exec.end');
        if (params.close) {
            stage = 'close';
            trace('close.begin');
            await Tools.System.terminal.close(sessionId);
            trace('close.end');
        }
        trace('return');
        return result;
    } catch (error) {
        trace(stage + '.failed');
        throw error;
    }
}
