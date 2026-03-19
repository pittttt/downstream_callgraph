#!/usr/bin/env python3
"""MCP Server: bridges Cursor to IntelliJ IDEA Call Graph plugin.

Requires IntelliJ IDEA running with the Call Graph plugin installed.
IDEA's built-in HTTP server (default port 63342) must be accessible.
"""

import httpx
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("callgraph")

IDEA_PORT_RANGE = range(63342, 63352)


async def _find_idea_port() -> int | None:
    async with httpx.AsyncClient(timeout=2) as client:
        for port in IDEA_PORT_RANGE:
            try:
                resp = await client.get(
                    f"http://localhost:{port}/api/callgraph/health"
                )
                if resp.status_code == 200:
                    return port
            except (httpx.ConnectError, httpx.TimeoutException):
                continue
    return None


async def _call_idea(params: dict) -> str:
    port = await _find_idea_port()
    if port is None:
        return (
            "Error: Cannot connect to IntelliJ IDEA. "
            "Make sure IDEA is running with the Call Graph plugin installed."
        )

    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.get(
            f"http://localhost:{port}/api/callgraph/generate",
            params=params,
        )
        if resp.status_code != 200:
            return f"Error ({resp.status_code}): {resp.text}"
        return resp.text


@mcp.tool()
async def generate_downstream_callgraph(
    class_name: str,
    method_name: str,
    include_source: bool = True,
    format: str = "markdown",
) -> str:
    """Generate downstream (callee) call graph for a Java method.
    Shows what methods the target method calls, recursively.

    Args:
        class_name: Fully qualified class name, e.g. com.example.service.OrderService
        method_name: Method name, e.g. processOrder
        include_source: Include source code of each method in output (default True)
        format: Output format - "markdown" (default, best for reading) or "json"
    """
    return await _call_idea({
        "className": class_name,
        "method": method_name,
        "direction": "downstream",
        "format": format,
        "includeSource": str(include_source).lower(),
    })


@mcp.tool()
async def generate_upstream_callgraph(
    class_name: str,
    method_name: str,
    include_source: bool = True,
    format: str = "markdown",
) -> str:
    """Generate upstream (caller) call graph for a Java method.
    Shows which methods call the target method, recursively upward.
    Useful for impact analysis when planning changes to a method.

    Args:
        class_name: Fully qualified class name, e.g. com.example.service.OrderService
        method_name: Method name, e.g. processOrder
        include_source: Include source code of each method in output (default True)
        format: Output format - "markdown" (default, best for reading) or "json"
    """
    return await _call_idea({
        "className": class_name,
        "method": method_name,
        "direction": "upstream",
        "format": format,
        "includeSource": str(include_source).lower(),
    })


@mcp.tool()
async def check_idea_connection() -> str:
    """Check if IntelliJ IDEA is running and the Call Graph plugin is available."""
    port = await _find_idea_port()
    if port is None:
        return (
            "Cannot connect to IntelliJ IDEA. "
            "Make sure IDEA is running with the Call Graph plugin installed."
        )
    return f"Connected to IntelliJ IDEA on port {port}."


if __name__ == "__main__":
    mcp.run(transport="stdio")
