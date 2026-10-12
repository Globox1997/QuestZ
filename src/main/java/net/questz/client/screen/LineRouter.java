package net.questz.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.questz.book.Quest;

import java.util.*;

@Environment(EnvType.CLIENT)
public class LineRouter {

    private static final float NODE_HALF = 13.0F;
    private static final float NODE_MARGIN = 2.0F;
    private static final float ATTACH_INSET = 4.0F;
    private static final float STUB = 6.0F;
    private static final float STEP = 5.0F;
    private static final float PORT = 5.0F;
    private static final float MIN_GAP = 4.0F;
    private static final float NODE_COST = 100.0F;
    private static final float OVERLAP_COST = 40.0F;
    private static final float DISTANCE_COST = 1.0F;

    public record Segment(float x1, float y1, float x2, float y2) {

        boolean vertical() {
            return this.x1 == this.x2;
        }

        float minX() {
            return Math.min(this.x1, this.x2);
        }

        float maxX() {
            return Math.max(this.x1, this.x2);
        }

        float minY() {
            return Math.min(this.y1, this.y2);
        }

        float maxY() {
            return Math.max(this.y1, this.y2);
        }
    }

    public record Route(Quest from, Quest to, List<Segment> segments) {
    }

    public interface Positions {
        float x(Quest quest);

        float y(Quest quest);
    }

    private record Box(String id, float minX, float minY, float maxX, float maxY) {

        boolean hits(Segment segment) {
            return segment.maxX() > this.minX && segment.minX() < this.maxX && segment.maxY() > this.minY && segment.minY() < this.maxY;
        }
    }

    private record Placed(String sourceId, Segment segment) {
    }

    private static final class Edge {
        final Quest from;
        final Quest to;
        final boolean horizontal;
        final float direction;
        final float sourceMain;
        final float sourcePerpendicular;
        final float targetMain;
        final float targetPerpendicular;
        final float sourceHalf;
        final float targetHalf;
        float port;

        Edge(Quest from, Quest to, Positions positions, boolean horizontal) {
            this.from = from;
            this.to = to;
            float dx = positions.x(to) - positions.x(from);
            float dy = positions.y(to) - positions.y(from);
            this.horizontal = horizontal;
            float delta = this.horizontal ? dx : dy;
            this.direction = delta < 0.0F ? -1.0F : 1.0F;
            this.sourceMain = this.horizontal ? positions.x(from) : positions.y(from);
            this.sourcePerpendicular = this.horizontal ? positions.y(from) : positions.x(from);
            this.targetMain = this.horizontal ? positions.x(to) : positions.y(to);
            this.targetPerpendicular = this.horizontal ? positions.y(to) : positions.x(to);
            this.sourceHalf = NODE_HALF * from.size();
            this.targetHalf = NODE_HALF * to.size();
        }

        float exitMain() {
            return this.sourceMain + this.direction * this.sourceHalf;
        }

        float entryMain() {
            return this.targetMain - this.direction * this.targetHalf;
        }

        float entryPerpendicular() {
            return this.targetPerpendicular + this.port;
        }

        List<Segment> segments(float trunk) {
            float attachExit = this.exitMain() - this.direction * ATTACH_INSET * this.from.size();
            float attachEntry = this.entryMain() + this.direction * ATTACH_INSET * this.to.size();
            List<Segment> segments = new ArrayList<>(3);
            segments.add(segment(this.horizontal, attachExit, this.sourcePerpendicular, trunk, this.sourcePerpendicular));
            segments.add(segment(this.horizontal, trunk, this.sourcePerpendicular, trunk, this.entryPerpendicular()));
            segments.add(segment(this.horizontal, trunk, this.entryPerpendicular(), attachEntry, this.entryPerpendicular()));
            return segments;
        }
    }

    private static final class Trunk {
        final String sourceId;
        final boolean horizontal;
        final float direction;
        final List<Edge> edges = new ArrayList<>();
        float coordinate;

        Trunk(String sourceId, boolean horizontal, float direction) {
            this.sourceId = sourceId;
            this.horizontal = horizontal;
            this.direction = direction;
        }
    }

    public static List<Route> route(Collection<Quest> quests, List<Quest[]> connections, Positions positions) {
        List<Box> boxes = new ArrayList<>();
        for (Quest quest : quests) {
            float half = NODE_HALF * quest.size() + NODE_MARGIN;
            boxes.add(new Box(quest.id(), positions.x(quest) - half, positions.y(quest) - half, positions.x(quest) + half, positions.y(quest) + half));
        }
        List<Edge> edges = new ArrayList<>();
        for (Quest[] connection : connections) {
            float dx = positions.x(connection[1]) - positions.x(connection[0]);
            float dy = positions.y(connection[1]) - positions.y(connection[0]);
            boolean preferHorizontal = Math.abs(dx) >= Math.abs(dy);
            Edge preferred = new Edge(connection[0], connection[1], positions, preferHorizontal);
            if (dx == 0.0F || dy == 0.0F) {
                edges.add(preferred);
                continue;
            }
            Edge alternative = new Edge(connection[0], connection[1], positions, !preferHorizontal);
            edges.add(standaloneCost(alternative, boxes) < standaloneCost(preferred, boxes) ? alternative : preferred);
        }
        assignPorts(edges);

        Map<Edge, Trunk> trunkOf = new HashMap<>();
        List<Trunk> trunks = buildTrunks(edges, trunkOf);
        List<Placed> placed = new ArrayList<>();
        for (Trunk trunk : trunks) {
            placeTrunk(trunk, boxes, placed);
        }

        List<Route> routes = new ArrayList<>();
        for (Edge edge : edges) {
            routes.add(new Route(edge.from, edge.to, edge.segments(trunkOf.get(edge).coordinate)));
        }
        return routes;
    }

    private static void assignPorts(List<Edge> edges) {
        Map<String, List<Edge>> groups = new LinkedHashMap<>();
        for (Edge edge : edges) {
            groups.computeIfAbsent(edge.to.id() + "|" + edge.horizontal + "|" + edge.direction, key -> new ArrayList<>()).add(edge);
        }
        for (List<Edge> group : groups.values()) {
            group.sort(Comparator.comparingDouble((Edge edge) -> edge.sourcePerpendicular).thenComparing(edge -> edge.from.id()));
            float limit = Math.max(0.0F, group.getFirst().targetHalf - 3.0F);
            for (int i = 0; i < group.size(); i++) {
                float offset = (i - (group.size() - 1) / 2.0F) * PORT;
                group.get(i).port = Math.max(-limit, Math.min(limit, offset));
            }
        }
    }

    private static List<Trunk> buildTrunks(List<Edge> edges, Map<Edge, Trunk> trunkOf) {
        Map<String, Trunk> trunks = new LinkedHashMap<>();
        for (Edge edge : edges) {
            Trunk trunk = trunks.computeIfAbsent(edge.from.id() + "|" + edge.horizontal + "|" + edge.direction,
                    key -> new Trunk(edge.from.id(), edge.horizontal, edge.direction));
            trunk.edges.add(edge);
            trunkOf.put(edge, trunk);
        }
        List<Trunk> sorted = new ArrayList<>(trunks.values());
        sorted.sort(Comparator.comparing((Trunk trunk) -> !trunk.horizontal)
                .thenComparingDouble(trunk -> trunk.edges.getFirst().sourceMain)
                .thenComparingDouble(trunk -> trunk.edges.getFirst().sourcePerpendicular)
                .thenComparing(trunk -> trunk.sourceId));
        return sorted;
    }

    private static float[] candidates(List<Edge> edges, float direction) {
        Edge first = edges.getFirst();
        float exit = first.exitMain();
        float nearestEntry = first.entryMain();
        for (Edge edge : edges) {
            if (direction * (edge.entryMain() - nearestEntry) < 0.0F) {
                nearestEntry = edge.entryMain();
            }
        }
        float gap = direction * (nearestEntry - exit);
        float inset = Math.max(0.0F, Math.min(STUB, gap / 2.0F));
        float start = exit + direction * inset;
        float end = nearestEntry - direction * inset;
        int steps = Math.max(0, (int) Math.floor(direction * (end - start) / STEP));
        if (steps == 0) {
            return new float[]{(start + end) / 2.0F};
        }
        float[] candidates = new float[steps + 1];
        for (int step = 0; step <= steps; step++) {
            candidates[step] = start + direction * STEP * step;
        }
        return candidates;
    }

    private static float standaloneCost(Edge edge, List<Box> boxes) {
        float best = Float.MAX_VALUE;
        float[] candidates = candidates(List.of(edge), edge.direction);
        for (int step = 0; step < candidates.length; step++) {
            float cost = DISTANCE_COST * step;
            for (Segment segment : edge.segments(candidates[step])) {
                for (Box box : boxes) {
                    if (!box.id().equals(edge.from.id()) && !box.id().equals(edge.to.id()) && box.hits(segment)) {
                        cost += NODE_COST;
                    }
                }
            }
            best = Math.min(best, cost);
        }
        return best;
    }

    private static void placeTrunk(Trunk trunk, List<Box> boxes, List<Placed> placed) {
        float[] candidates = candidates(trunk.edges, trunk.direction);
        float best = candidates[0];
        float bestCost = Float.MAX_VALUE;
        for (int step = 0; step < candidates.length; step++) {
            float cost = cost(trunk, candidates[step], boxes, placed) + DISTANCE_COST * step;
            if (cost < bestCost) {
                bestCost = cost;
                best = candidates[step];
            }
        }
        trunk.coordinate = best;
        for (Edge edge : trunk.edges) {
            for (Segment segment : edge.segments(best)) {
                placed.add(new Placed(trunk.sourceId, segment));
            }
        }
    }

    private static float cost(Trunk trunk, float candidate, List<Box> boxes, List<Placed> placed) {
        float cost = 0.0F;
        Set<Segment> segments = new LinkedHashSet<>();
        for (Edge edge : trunk.edges) {
            List<Segment> edgeSegments = edge.segments(candidate);
            segments.addAll(edgeSegments);
            for (Box box : boxes) {
                if (box.id().equals(edge.from.id()) || box.id().equals(edge.to.id())) {
                    continue;
                }
                for (Segment segment : edgeSegments) {
                    if (box.hits(segment)) {
                        cost += NODE_COST;
                    }
                }
            }
        }
        for (Segment segment : segments) {
            for (Placed other : placed) {
                if (!other.sourceId().equals(trunk.sourceId) && collinearOverlap(segment, other.segment())) {
                    cost += OVERLAP_COST;
                }
            }
        }
        return cost;
    }

    private static boolean collinearOverlap(Segment a, Segment b) {
        if (a.vertical() != b.vertical()) {
            return false;
        }
        if (a.vertical()) {
            return Math.abs(a.x1() - b.x1()) < MIN_GAP && a.maxY() > b.minY() + 1.0F && b.maxY() > a.minY() + 1.0F;
        }
        return Math.abs(a.y1() - b.y1()) < MIN_GAP && a.maxX() > b.minX() + 1.0F && b.maxX() > a.minX() + 1.0F;
    }

    private static Segment segment(boolean horizontal, float main1, float perpendicular1, float main2, float perpendicular2) {
        return horizontal ? new Segment(main1, perpendicular1, main2, perpendicular2) : new Segment(perpendicular1, main1, perpendicular2, main2);
    }

}
