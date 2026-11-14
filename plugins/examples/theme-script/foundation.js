/** CommonJS entry for an .oktheme project; no external drawing assets required. */
module.exports = {
  apiVersion: 1,
  render(context) {
    const appearance = context.appearance;
    const dark = context.mode === "dark";
    return {
      ...appearance,
      apiVersion: 1,
      surfaces: {
        ...appearance.surfaces,
        "page.packages": {
          fillGradient: {
            type: "linear",
            start: { x: 0, y: 0 }, end: { x: 1, y: 1 },
            stops: [
              { offset: 0, color: dark ? "#FF17172E" : "#FFF4E9FF" },
              { offset: 1, color: dark ? "#FF18373A" : "#FFD9F6ED" }
            ]
          }
        }
      },
      textStyles: {
        ...appearance.textStyles,
        "chat.modelName": {
          family: "monospace", scale: 1.15, weight: 600,
          color: dark ? "#FFC7B5FF" : "#FF493181",
          stroke: { color: dark ? "#FF17172E" : "#FFFFFFFF", width: 0.1 }
        }
      }
    };
  }
};
