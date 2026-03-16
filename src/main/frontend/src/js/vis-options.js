const options = {
    layout: {
        hierarchical: {
            enabled: true,
            direction: 'UD',
            sortMethod: 'directed',
            levelSeparation: 120,
            nodeSpacing: 180
        }
    },
    nodes: {
        shape: "box",
        font: {
            multi: "md",
            size: 14
        }
    },
    edges: {
        arrows: {
            to: {
                enabled: true
            },
        },
        font: {
            strokeWidth: 5,
            size: 12,
            align: "middle"
        },
    },
    interaction: {
        zoomSpeed: 0.25,
        hover: true
    },
    physics: {
        enabled: false
    }
};

module.exports = options;
