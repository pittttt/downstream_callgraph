import vis from "vis-network/standalone/umd/vis-network.min.js";
import options from "./vis-options";
import "@fortawesome/fontawesome-free/css/all.min.css";

window.JavaBridge = {
    goToSource: (referenceHashCode) => {
    },
    saveAsHtml: (unused) => {
    },
    exportMarkdown: (unused) => {
    },
    generateGraph: (unused) => {
    },
    openSettings: (unused) => {
    }
}

const messageElement = document.getElementById("message");
const networkElement = document.getElementById("network");
const generateMessage = document.getElementById("generateMessage");
const showAllButton = document.getElementById("showAllButton");
const statsOverlay = document.getElementById("stats-overlay");

const network = new vis.Network(networkElement, {}, options);
let hiddenNodes = new Set();
let selectedNodeId = null;
let isGraphFitted = false;
let isGraphGenerated = false;

network.on("click", function (params) {
    if (params.nodes.length === 1) {
        selectedNodeId = params.nodes[0];
        JavaBridge.goToSource(params.nodes[0]);
    } else {
        selectedNodeId = null;
    }
    if (params.edges.length === 1) {
        JavaBridge.goToSource(params.edges[0]);
    }
    updateButtonVisibility();
});

network.on("stabilizationProgress", function (params) {
    const message = "Stabilization progress: " + Math.round(params.iterations / params.total * 100) + "%";
    showMessage(message);
});

network.on("stabilizationIterationsDone", function () {
    hideMessage();
    isGraphGenerated = true;
    fit();
    showGraphControls();
});

network.on("fit", () => {
    isGraphFitted = true;
    updateButtonVisibility();
});

network.on("dragEnd", () => {
    isGraphFitted = false;
    updateButtonVisibility();
});

network.on("zoom", () => {
    isGraphFitted = false;
    updateButtonVisibility();
});

function hideSelectedNode() {
    if (selectedNodeId !== null) {
        hiddenNodes.add(selectedNodeId);
        network.body.data.nodes.update({id: selectedNodeId, hidden: true});
        selectedNodeId = null;
        updateShowAllButton();
        updateButtonVisibility();
    }
}

function showAllNodes() {
    if (hiddenNodes.size > 0) {
        const hiddenNodesArray = Array.from(hiddenNodes);
        hiddenNodesArray.forEach(nodeId => {
            network.body.data.nodes.update({id: nodeId, hidden: false});
        });
        hiddenNodes.clear();
        updateShowAllButton();
    }
}

function updateShowAllButton() {
    if (hiddenNodes.size > 0) {
        showAllButton.classList.remove("hidden");
    } else {
        showAllButton.classList.add("hidden");
    }
}

function updateButtonVisibility() {
    const fitButton = document.querySelector('button[onclick="fit()"]');
    const hideNodeButton = document.querySelector('button[onclick="hideSelectedNode()"]');

    if (fitButton) {
        if (!isGraphGenerated || isGraphFitted) {
            fitButton.classList.add("hidden");
        } else {
            fitButton.classList.remove("hidden");
        }
    }

    if (hideNodeButton) {
        if (selectedNodeId !== null) {
            hideNodeButton.classList.remove("hidden");
        } else {
            hideNodeButton.classList.add("hidden");
        }
    }
}

function showMessage(message) {
    if (!message || message.trim() === '') {
        message = "Place your caret on a method, right-click, or use Alt+Shift+D shortcut to generate a downstream call graph.";
    }
    messageElement.innerHTML = message;
    messageElement.classList.remove("hidden");
    networkElement.classList.add("hidden");
}

function hideMessage() {
    messageElement.classList.add("hidden");
    networkElement.classList.remove("hidden");
}

function showGraphControls() {
    for (let generatedGraphController of document.getElementsByClassName("generatedGraphController")) {
        generatedGraphController.classList.remove("hidden");
    }
    updateShowAllButton();
    updateButtonVisibility();
}

function hideGraphControls() {
    for (let generatedGraphController of document.getElementsByClassName("generatedGraphController")) {
        generatedGraphController.classList.add("hidden");
    }
}

function updateStats(maxDepth, totalMethods) {
    if (statsOverlay) {
        statsOverlay.innerHTML = "Max Depth: " + maxDepth + " &nbsp;|&nbsp; Methods: " + totalMethods;
    }
}

function clearStats() {
    if (statsOverlay) {
        statsOverlay.innerHTML = "";
    }
}

function updateNetwork(data) {
    hideGraphControls();
    hiddenNodes.clear();
    selectedNodeId = null;
    isGraphFitted = false;
    isGraphGenerated = false;
    updateShowAllButton();

    var nodeCount = data.nodes ? data.nodes.length : 0;
    var edgeCount = data.edges ? data.edges.length : 0;
    showMessage("Rendering graph... (" + nodeCount + " nodes, " + edgeCount + " edges)");

    var maxLevel = 0;
    if (data.nodes) {
        for (var i = 0; i < data.nodes.length; i++) {
            var lvl = data.nodes[i].level || 0;
            if (lvl > maxLevel) maxLevel = lvl;
        }
    }
    updateStats(maxLevel, nodeCount);

    try {
        options.groups = data.groups;
        network.setOptions(options);
        network.setData(data);

        // Hierarchical layout with physics disabled won't fire stabilization events,
        // so we directly finalize the graph after a short delay for rendering.
        if (options.layout && options.layout.hierarchical && options.layout.hierarchical.enabled
            && options.physics && !options.physics.enabled) {
            setTimeout(function () {
                hideMessage();
                isGraphGenerated = true;
                fit();
                showGraphControls();
            }, 200);
        } else {
            network.stabilize();
        }
    } catch (e) {
        showMessage("Error: " + e);
    }
}

function fit() {
    network.fit();
    isGraphFitted = true;
    updateButtonVisibility();
}

const MESSAGE_TYPE_SUCCESS = "+";
const MESSAGE_TYPE_ERROR = "-";

function setGenerateMessage(message) {
    if (!message || message.trim() === '') {
        message = "-PLACE YOUR CARET ON A METHOD";
    }

    let messageTypeFlag = message.substring(0, 1);
    let classToSet = "navbuttonMessage "
    if (messageTypeFlag === MESSAGE_TYPE_SUCCESS) {
        classToSet += "navbuttonMessage-success";
    } else if (messageTypeFlag === MESSAGE_TYPE_ERROR) {
        classToSet += "navbuttonMessage-error";
    }
    generateMessage.className = classToSet;
    generateMessage.innerHTML = message.substring(1);
}

function updateMessageTextColor(backgroundColor) {
    backgroundColor = backgroundColor.replace('#', '');

    const r = parseInt(backgroundColor.substr(0, 2), 16);
    const g = parseInt(backgroundColor.substr(2, 2), 16);
    const b = parseInt(backgroundColor.substr(4, 2), 16);

    const brightness = (0.299 * r + 0.587 * g + 0.114 * b) / 255;

    if (brightness > 0.5) {
        messageElement.style.color = "black";
    } else {
        messageElement.style.color = "white";
    }
}

function resetDefaultMessages() {
    showMessage();
    setGenerateMessage();
}

window.updateNetwork = updateNetwork;
window.updateStats = updateStats;
window.clearStats = clearStats;
window.fit = fit;
window.showMessage = showMessage;
window.showGraphControls = showGraphControls;
window.setGenerateMessage = setGenerateMessage;
window.updateMessageTextColor = updateMessageTextColor;
window.resetDefaultMessages = resetDefaultMessages;
window.hideSelectedNode = hideSelectedNode;
window.showAllNodes = showAllNodes;

resetDefaultMessages();
updateButtonVisibility();
