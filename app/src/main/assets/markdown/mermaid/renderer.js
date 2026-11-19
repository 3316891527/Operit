"use strict";

(() => {
    const viewport = document.getElementById("viewport");
    const diagram = document.getElementById("diagram");
    const status = document.getElementById("status");
    const controls = document.querySelectorAll("button");
    const pointers = new Map();
    let renderVersion = 0;
    let svg = null;
    let naturalBox = null;
    let visibleBox = null;
    let zoom = 1;
    let gesture = null;
    let cancelViewportWait = () => {};

    function applyViewBox() {
        svg.setAttribute("viewBox", `${visibleBox.x} ${visibleBox.y} ${visibleBox.width} ${visibleBox.height}`);
    }

    function fitDiagram() {
        if (!svg || !naturalBox) return;
        zoom = 1;
        visibleBox = { ...naturalBox };
        applyViewBox();
    }

    function svgPoint(clientX, clientY) {
        return new DOMPoint(clientX, clientY).matrixTransform(svg.getScreenCTM().inverse());
    }

    function viewportCenter() {
        const bounds = viewport.getBoundingClientRect();
        return { x: bounds.x + bounds.width / 2, y: bounds.y + bounds.height / 2 };
    }

    function zoomAt(nextZoom, screenPoint, anchor) {
        if (!svg || !visibleBox) return;
        const point = svgPoint(screenPoint.x, screenPoint.y);
        const target = anchor || point;
        const fractionX = (point.x - visibleBox.x) / visibleBox.width;
        const fractionY = (point.y - visibleBox.y) / visibleBox.height;
        zoom = Math.max(.1, Math.min(16, nextZoom));
        const width = naturalBox.width / zoom;
        const height = naturalBox.height / zoom;
        visibleBox = { x: target.x - fractionX * width, y: target.y - fractionY * height, width, height };
        applyViewBox();
    }

    function startGesture() {
        gesture = null;
        if (!svg || !visibleBox) return;
        const points = [...pointers.values()];
        if (points.length === 1) {
            gesture = { anchor: svgPoint(points[0].x, points[0].y) };
        } else if (points.length === 2) {
            const center = { x: (points[0].x + points[1].x) / 2, y: (points[0].y + points[1].y) / 2 };
            gesture = {
                anchor: svgPoint(center.x, center.y),
                distance: Math.hypot(points[0].x - points[1].x, points[0].y - points[1].y),
                zoom,
            };
        }
    }

    // 拖动和双指缩放都在 SVG 坐标中进行，不再叠加 CSS transform 与滚动容器坐标。
    viewport.addEventListener("pointerdown", event => {
        if (!svg || !visibleBox || event.button !== 0) return;
        pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
        viewport.setPointerCapture(event.pointerId);
        startGesture();
    });
    viewport.addEventListener("pointermove", event => {
        if (!pointers.has(event.pointerId) || !gesture) return;
        pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
        const points = [...pointers.values()];
        if (points.length === 1) {
            const point = svgPoint(points[0].x, points[0].y);
            visibleBox.x += gesture.anchor.x - point.x;
            visibleBox.y += gesture.anchor.y - point.y;
            applyViewBox();
        } else if (points.length === 2 && gesture.distance > 0) {
            const center = { x: (points[0].x + points[1].x) / 2, y: (points[0].y + points[1].y) / 2 };
            const distance = Math.hypot(points[0].x - points[1].x, points[0].y - points[1].y);
            zoomAt(gesture.zoom * distance / gesture.distance, center, gesture.anchor);
        }
    });
    function endPointer(event) {
        pointers.delete(event.pointerId);
        startGesture();
    }
    viewport.addEventListener("pointerup", endPointer);
    viewport.addEventListener("pointercancel", endPointer);
    viewport.addEventListener("lostpointercapture", endPointer);
    viewport.addEventListener("wheel", event => {
        if (!svg || !visibleBox) return;
        event.preventDefault();
        zoomAt(zoom * Math.exp(-event.deltaY * .002), { x: event.clientX, y: event.clientY });
    }, { passive: false });
    viewport.addEventListener("dblclick", fitDiagram);
    document.getElementById("zoom-in").addEventListener("click", () => zoomAt(zoom * 1.25, viewportCenter()));
    document.getElementById("zoom-out").addEventListener("click", () => zoomAt(zoom / 1.25, viewportCenter()));
    document.getElementById("fit").addEventListener("click", fitDiagram);

    function waitForViewport(version) {
        return new Promise(resolve => {
            const observer = new ResizeObserver(() => {
                if (version !== renderVersion) { observer.disconnect(); resolve(false); return; }
                if (viewport.clientWidth > 0 && viewport.clientHeight > 0) {
                    observer.disconnect();
                    cancelViewportWait = () => {};
                    resolve(true);
                }
            });
            cancelViewportWait = () => { observer.disconnect(); resolve(false); };
            observer.observe(viewport);
        });
    }

    window.renderMermaid = async ({ code, errorMessage, loadingMessage }) => {
        const version = ++renderVersion;
        const renderId = `mermaid-diagram-${version}`;
        cancelViewportWait();
        pointers.clear();
        gesture = null;
        svg = null;
        naturalBox = null;
        visibleBox = null;
        diagram.replaceChildren();
        diagram.style.visibility = "hidden";
        status.className = "";
        status.hidden = false;
        status.textContent = loadingMessage;
        controls.forEach(button => { button.disabled = true; });
        try {
            // WebView 首次布局可能尚未完成，不能以零宽度视口计算文字和缩放。
            if (!await waitForViewport(version) || version !== renderVersion) return;
            await document.fonts.ready;
            if (version !== renderVersion) return;
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
            if (version !== renderVersion) return;
            diagram.innerHTML = result.svg;
            const renderedSvg = diagram.querySelector("svg");
            if (!renderedSvg) throw new Error("Mermaid did not return an SVG diagram");
            renderedSvg.setAttribute("width", "100%");
            renderedSvg.setAttribute("height", "100%");
            renderedSvg.setAttribute("preserveAspectRatio", "xMidYMid meet");
            await document.fonts.ready;
            await new Promise(resolve => requestAnimationFrame(resolve));
            if (version !== renderVersion) return;
            // 使用实际绘制范围建立视框，不能把上游 viewBox 的宽高当成固定像素画布。
            const bounds = renderedSvg.getBBox();
            if (![bounds.x, bounds.y, bounds.width, bounds.height].every(Number.isFinite) ||
                bounds.width <= 0 || bounds.height <= 0) {
                throw new Error("Mermaid returned an empty diagram");
            }
            const padding = 12;
            naturalBox = {
                x: bounds.x - padding,
                y: bounds.y - padding,
                width: bounds.width + padding * 2,
                height: bounds.height + padding * 2,
            };
            svg = renderedSvg;
            fitDiagram();
            diagram.style.visibility = "visible";
            status.hidden = true;
            controls.forEach(button => { button.disabled = false; });
        } catch (error) {
            if (version !== renderVersion) return;
            svg = null;
            naturalBox = null;
            visibleBox = null;
            diagram.replaceChildren();
            document.getElementById(`d${renderId}`)?.remove();
            status.className = "error";
            status.hidden = false;
            status.textContent = `${errorMessage}\n${error instanceof Error ? error.message : error}`;
            console.error("Mermaid render failed", error);
        }
    };
})();