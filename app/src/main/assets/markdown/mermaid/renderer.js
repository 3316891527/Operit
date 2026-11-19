"use strict";

(() => {
    const viewport = document.getElementById("viewport");
    const sizer = document.getElementById("sizer");
    const diagram = document.getElementById("diagram");
    const status = document.getElementById("status");
    const controls = document.querySelectorAll("button");
    let renderVersion = 0;
    let width = 0;
    let height = 0;
    let scale = 1;
    let fitScale = 1;
    let pinchDistance = 0;
    let pinchScale = 1;

    function applyZoom(nextScale) {
        scale = nextScale;
        sizer.style.width = `${width * scale}px`;
        sizer.style.height = `${height * scale}px`;
        diagram.style.transform = `scale(${scale})`;
    }

    function fitDiagram() {
        if (!width) return;
        fitScale = Math.min(1, (viewport.clientWidth - 32) / width);
        applyZoom(fitScale);
        viewport.scrollTo(0, 0);
    }

    // 工具按钮与双指缩放共享一个比例，避免页面缩放和图表缩放叠加。
    document.getElementById("zoom-in").addEventListener("click", () => applyZoom(Math.min(scale * 1.25, 4)));
    document.getElementById("zoom-out").addEventListener("click", () => applyZoom(Math.max(scale / 1.25, Math.min(.05, fitScale))));
    document.getElementById("fit").addEventListener("click", fitDiagram);
    viewport.addEventListener("touchstart", (event) => {
        if (event.touches.length !== 2 || !width) return;
        pinchDistance = Math.hypot(
            event.touches[0].clientX - event.touches[1].clientX,
            event.touches[0].clientY - event.touches[1].clientY,
        );
        pinchScale = scale;
    }, { passive: true });
    viewport.addEventListener("touchmove", (event) => {
        if (event.touches.length !== 2 || !pinchDistance) return;
        event.preventDefault();
        const distance = Math.hypot(
            event.touches[0].clientX - event.touches[1].clientX,
            event.touches[0].clientY - event.touches[1].clientY,
        );
        applyZoom(Math.min(4, Math.max(Math.min(.05, fitScale), pinchScale * distance / pinchDistance)));
    }, { passive: false });
    viewport.addEventListener("touchend", () => { pinchDistance = 0; });
    viewport.addEventListener("touchcancel", () => { pinchDistance = 0; });
    window.addEventListener("resize", fitDiagram);

    window.renderMermaid = async ({ code, errorMessage, loadingMessage }) => {
        const version = ++renderVersion;
        const renderId = `mermaid-diagram-${version}`;
        diagram.replaceChildren();
        width = 0;
        height = 0;
        sizer.style.width = "0px";
        sizer.style.height = "0px";
        status.className = "";
        status.hidden = false;
        status.textContent = loadingMessage;
        controls.forEach((button) => { button.disabled = true; });
        try {
            mermaid.initialize({
                startOnLoad: false,
                securityLevel: "strict",
                suppressErrorRendering: true,
                secure: ["secure", "securityLevel", "startOnLoad", "maxTextSize", "maxEdges", "suppressErrorRendering"],
                theme: "dark",
                fontFamily: "sans-serif",
                flowchart: { htmlLabels: false, useMaxWidth: false },
            });
            const result = await mermaid.render(renderId, code);
            // 切换源码时旧任务可能晚到，只显示最新一次请求的图表。
            if (version !== renderVersion) return;
            diagram.innerHTML = result.svg;
            const svg = diagram.querySelector("svg");
            if (!svg) throw new Error("Mermaid did not return an SVG diagram");
            const bounds = svg.viewBox.baseVal;
            width = bounds.width;
            height = bounds.height;
            if (!(width > 0 && height > 0)) throw new Error("Mermaid returned an empty diagram");
            svg.style.width = `${width}px`;
            svg.style.height = `${height}px`;
            status.hidden = true;
            fitDiagram();
            controls.forEach((button) => { button.disabled = false; });
        } catch (error) {
            if (version !== renderVersion) return;
            diagram.replaceChildren();
            document.getElementById(`d${renderId}`)?.remove();
            status.className = "error";
            status.hidden = false;
            // 错误和源码都作为文本处理，不把异常内容插入 HTML。
            status.textContent = `${errorMessage}\n${error instanceof Error ? error.message : error}`;
            console.error("Mermaid render failed", error);
        }
    };
})();
