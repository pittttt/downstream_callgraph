import vis from "vis-network/standalone/umd/vis-network.min.js";
import options from "./vis-options";

const networkElement = document.getElementById("network");

const network = new vis.Network(networkElement, {}, options);

function updateNetwork(data) {
    options.groups = data.groups;
    network.setOptions(options);
    network.setData(data);
    if (options.layout && options.layout.hierarchical && options.layout.hierarchical.enabled
        && options.physics && !options.physics.enabled) {
        setTimeout(function () { network.fit(); }, 200);
    } else {
        network.stabilize();
    }
}

window.updateNetwork = updateNetwork;
