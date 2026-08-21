package de.open4me.hibiscus.reports.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.open4me.hibiscus.reports.model.SankeyGraph;

final class SankeyLayout
{
    static final int WIDTH = 1380;
    static final int NODE_WIDTH = 20;
    static final int DEFAULT_LINK_COLOR = 0x919191;
    private static final int GAP = 32;
    private static final int LABEL_ROW_HEIGHT = 40;
    private static final int TOP = 32;
    private static final int BOTTOM = 32;
    private static final int[] COLUMN_X = { 250, 530, 820, 1110 };

    private SankeyLayout()
    {
    }

    static int preferredHeight(SankeyGraph graph)
    {
        int maxNodes = 1;
        if (graph != null)
        {
            for (int layer = 0; layer < 4; layer++)
            {
                int currentLayer = layer;
                int count = (int) graph.nodes().stream().filter(node -> node.layer() == currentLayer).count();
                maxNodes = Math.max(maxNodes, count);
            }
        }
        return Math.max(560, TOP + BOTTOM + maxNodes * LABEL_ROW_HEIGHT
            + Math.max(0, maxNodes - 1) * GAP);
    }

    static Scene create(SankeyGraph graph, int height)
    {
        if (graph == null)
            return new Scene(WIDTH, height, List.of(), List.of());

        double maximumLayerTotal = 0d;
        for (int layer = 0; layer < 4; layer++)
        {
            int currentLayer = layer;
            maximumLayerTotal = Math.max(maximumLayerTotal, graph.nodes().stream()
                .filter(node -> node.layer() == currentLayer).mapToDouble(SankeyGraph.Node::amount).sum());
        }
        int usable = Math.max(100, height - TOP - BOTTOM);
        int maximumGaps = 0;
        for (int layer = 0; layer < 4; layer++)
        {
            int currentLayer = layer;
            int count = (int) graph.nodes().stream().filter(node -> node.layer() == currentLayer).count();
            maximumGaps = Math.max(maximumGaps, Math.max(0, count - 1));
        }
        double scale = maximumLayerTotal <= 0d ? 1d
            : Math.max(1d, usable - maximumGaps * GAP) / maximumLayerTotal;
        scale = Math.max(0.0001d, scale);

        List<NodePlacement> nodes = new ArrayList<>();
        Map<String, NodePlacement> byId = new HashMap<>();
        for (int layer = 0; layer < 3; layer++)
        {
            int currentLayer = layer;
            List<SankeyGraph.Node> layerNodes = graph.nodes().stream()
                .filter(node -> node.layer() == currentLayer).toList();
            int totalHeight = 0;
            for (SankeyGraph.Node node : layerNodes)
                totalHeight += nodeHeight(node, scale);
            totalHeight += Math.max(0, layerNodes.size() - 1) * GAP;
            int y = Math.max(TOP, TOP + (usable - totalHeight) / 2);
            for (SankeyGraph.Node node : layerNodes)
            {
                int nodeHeight = nodeHeight(node, scale);
                NodePlacement placement = new NodePlacement(node,
                    new Bounds(COLUMN_X[layer], y, NODE_WIDTH, nodeHeight));
                nodes.add(placement);
                byId.put(node.id(), placement);
                y += nodeHeight + GAP;
            }
        }
        placeExpandedChildren(graph, nodes, byId, scale, height);

        List<LinkPlacement> links = new ArrayList<>();
        Map<String, Double> outgoing = new HashMap<>();
        Map<String, Double> incoming = new HashMap<>();
        for (SankeyGraph.Link link : graph.links())
        {
            NodePlacement source = byId.get(link.sourceId());
            NodePlacement target = byId.get(link.targetId());
            if (source == null || target == null || link.amount() <= 0d)
                continue;
            double thickness = Math.max(1d, link.amount() * scale);
            double sourceOffset = outgoing.getOrDefault(link.sourceId(), 0d);
            double targetOffset = incoming.getOrDefault(link.targetId(), 0d);
            float sx = source.bounds().x() + source.bounds().width();
            float tx = target.bounds().x();
            float sy1 = (float) (source.bounds().y() + sourceOffset);
            float sy2 = (float) Math.min(source.bounds().bottom(), sy1 + thickness);
            float ty1 = (float) (target.bounds().y() + targetOffset);
            float ty2 = (float) Math.min(target.bounds().bottom(), ty1 + thickness);
            float bend = (tx - sx) * 0.48f;
            links.add(new LinkPlacement(link, sx, sy1, sy2, tx, ty1, ty2, bend,
                linkColor(source.node(), target.node())));
            outgoing.put(link.sourceId(), sourceOffset + thickness);
            incoming.put(link.targetId(), targetOffset + thickness);
        }
        return new Scene(WIDTH, height, List.copyOf(nodes), List.copyOf(links));
    }

    private static void placeExpandedChildren(SankeyGraph graph, List<NodePlacement> nodes,
                                              Map<String, NodePlacement> byId, double scale, int height)
    {
        Map<String, List<SankeyGraph.Node>> childrenByParent = new LinkedHashMap<>();
        Map<String, SankeyGraph.Node> children = new HashMap<>();
        for (SankeyGraph.Node node : graph.nodes())
        {
            if (node.layer() == 3)
                children.put(node.id(), node);
        }
        for (SankeyGraph.Link link : graph.links())
        {
            SankeyGraph.Node child = children.get(link.targetId());
            NodePlacement parent = byId.get(link.sourceId());
            if (child != null && parent != null && parent.node().layer() == 2)
                childrenByParent.computeIfAbsent(parent.node().id(), ignored -> new ArrayList<>()).add(child);
        }

        List<ChildBlock> blocks = new ArrayList<>();
        for (Map.Entry<String, List<SankeyGraph.Node>> entry : childrenByParent.entrySet())
        {
            NodePlacement parent = byId.get(entry.getKey());
            if (parent != null)
                blocks.add(new ChildBlock(parent, entry.getValue()));
        }
        blocks.sort(Comparator.comparingDouble(block -> block.parent().bounds().y()));

        int nextY = TOP;
        List<ChildPlacement> placements = new ArrayList<>();
        for (ChildBlock block : blocks)
        {
            int blockHeight = block.height(scale);
            int preferredY = Math.round(block.parent().bounds().y()
                + (block.parent().bounds().height() - blockHeight) / 2f);
            int y = Math.max(nextY, Math.max(TOP, preferredY));
            for (SankeyGraph.Node child : block.children())
            {
                int childHeight = nodeHeight(child, scale);
                placements.add(new ChildPlacement(child, y, childHeight));
                y += childHeight + GAP;
            }
            nextY = y;
        }

        int lastBottom = placements.isEmpty() ? TOP : placements.get(placements.size() - 1).bottom();
        int shift = Math.min(0, height - BOTTOM - lastBottom);
        for (ChildPlacement placement : placements)
        {
            NodePlacement node = new NodePlacement(placement.node(),
                new Bounds(COLUMN_X[3], placement.y() + shift, NODE_WIDTH, placement.height()));
            nodes.add(node);
            byId.put(placement.node().id(), node);
        }
    }

    private static int nodeHeight(SankeyGraph.Node node, double scale)
    {
        return Math.max(12, (int) Math.round(node.amount() * scale));
    }

    private static int linkColor(SankeyGraph.Node source, SankeyGraph.Node target)
    {
        if (source == null || target == null)
            return DEFAULT_LINK_COLOR;
        return target.layer() == 1 ? source.color() : target.color();
    }

    private record ChildBlock(NodePlacement parent, List<SankeyGraph.Node> children)
    {
        private int height(double scale)
        {
            int result = Math.max(0, children.size() - 1) * GAP;
            for (SankeyGraph.Node child : children)
                result += nodeHeight(child, scale);
            return result;
        }
    }

    private record ChildPlacement(SankeyGraph.Node node, int y, int height)
    {
        private int bottom()
        {
            return y + height;
        }
    }

    record Scene(int width, int height, List<NodePlacement> nodes, List<LinkPlacement> links)
    {
    }

    record NodePlacement(SankeyGraph.Node node, Bounds bounds)
    {
    }

    record LinkPlacement(SankeyGraph.Link link, float sourceX, float sourceTop, float sourceBottom,
                         float targetX, float targetTop, float targetBottom, float bend, int color)
    {
    }

    record Bounds(float x, float y, float width, float height)
    {
        float bottom()
        {
            return y + height;
        }

        boolean contains(int px, int py)
        {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }
}
