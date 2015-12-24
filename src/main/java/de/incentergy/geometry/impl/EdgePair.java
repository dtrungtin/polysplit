package de.incentergy.geometry.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineSegment;
import com.vividsolutions.jts.geom.Polygon;

import de.incentergy.geometry.utils.GeometryFactoryUtils;
import de.incentergy.geometry.utils.GeometryUtils;

/**
 * Represents a pair of edges on polygon's exterior ring.<br>
 * Warning: direction of edges is assumed to be the same as in the polygon's exterior ring.
 * <p>
 * Possible lines of cut are located in one of:
 * <ul>
 * <li>T1 - First triangle, may not exist in some cases</li>
 * <li>Trapezoid - Trapezoid, always present</li>
 * <li>T2 - Second triangle, may not exist in some cases</li>
 * <ul>
 *
 * <pre>
 *                                edgeA
 *            edgeA.p0 .____________________________. edgeA.p1
 *                    /|                            |\
 *                   /                                \
 *   outsideEdge2   /  |                            |  \   outsideEdge1
 *                 /                                    \
 *                / T2 |        Trapezoid           | T1 \
 *               /                                        \
 *              .______.____________________________|______.
 *        edgeB.p1                edgeB                    edgeB.p0
 *                     ^                            ^
 *                 projected1                  projected0
 * </pre>
 */
class EdgePair {

    private final LineSegment edgeA;
    private final LineSegment edgeB;

    private ProjectedVertex projected0;          // projected p0
    private ProjectedVertex projected1;          // projected p1

    public EdgePair(LineSegment edgeA, LineSegment edgeB) {
        // determine the point where the edges would intersect if they were infinite lines
        Coordinate intersectionPoint = GeometryUtils.getIntersectionPoint(edgeA, edgeB);

        this.edgeA = edgeA;
        this.edgeB = edgeB;

        // there will be 2 projected points at most
        projected0 = getProjectedVertex(edgeA.p1, edgeB, intersectionPoint);
        if (projected0.isNotValid()) {
            projected0 = getProjectedVertex(edgeB.p0, edgeA, intersectionPoint);
        }
        projected1 = getProjectedVertex(edgeA.p0, edgeB, intersectionPoint);
        if (projected1.isNotValid()) {
            projected1 = getProjectedVertex(edgeB.p1, edgeA, intersectionPoint);
        }
    }

    private ProjectedVertex getProjectedVertex(Coordinate point, LineSegment edge, Coordinate intersectionPoint) {
        Coordinate projectionPoint = GeometryUtils.getProjectedPoint(point, edge, intersectionPoint);
        return projectionPoint != null ? new ProjectedVertex(projectionPoint, edge) : ProjectedVertex.INVALID;
    }

    public EdgePairSubpolygons getSubpolygons() {
        return new EdgePairSubpolygons(edgeA, edgeB, projected0, projected1);
    }

    @Override
    public String toString() {
        return "EdgePair [edgeA=" + edgeA + ", edgeB=" + edgeB + "]";
    }

    private static class ProjectedVertex extends Coordinate {
        private static final long serialVersionUID = 1L;
        private static final ProjectedVertex INVALID = new ProjectedVertex();

        private final LineSegment edge;
        private final boolean valid;            // ProjectedVertex is invalid if the projected point lies outside of the edge (is null)

        private ProjectedVertex() {
            this.valid = false;
            this.edge = null;
        }

        public ProjectedVertex(Coordinate coord, LineSegment edge) {
            super(coord);
            this.valid = true;
            this.edge = edge;
        }

        public boolean isValid() {
            return valid;
        }

        public boolean isNotValid() {
            return !valid;
        }

        public boolean isOnEdge(LineSegment edge) {
            return valid && this.edge.equalsTopo(edge);
        }

        @Override
        public String toString() {
            return valid ? super.toString() : "(INVALID)";
        }
    }

    /**
     * This class represents the 3 possible polygons in which the minimum cut can be located
     */
    static class EdgePairSubpolygons {
        private final LineSegment edgeA;
        private final LineSegment edgeB;

        private final Polygon triangle1;
        private final Polygon trapezoid;
        private final Polygon triangle2;
        private final double triangle1Area;
        private final double trapezoidArea;
        private final double triangle2Area;

        private EdgePairSubpolygons(LineSegment edgeA, LineSegment edgeB, ProjectedVertex projected0, ProjectedVertex projected1) {
            this.edgeA = Objects.requireNonNull(edgeA, "Edge A is required");
            this.edgeB = Objects.requireNonNull(edgeB, "Edge B is required");

            // build triangles if corresponding projected points are valid
            triangle1 = projected0.isValid() ? GeometryFactoryUtils.createTriangle(edgeA.p1, projected0, edgeB.p0) : null;
            triangle2 = projected1.isValid() ? GeometryFactoryUtils.createTriangle(edgeA.p0, projected1, edgeB.p1) : null;
            triangle1Area = triangle1 != null ? triangle1.getArea() : 0;
            triangle2Area = triangle2 != null ? triangle2.getArea() : 0;

            // build a trapezoid:
            // 1) if projected1 is on edgeA, add projected1, else add edgeA.p0
            // 2) if projected0 is on edgeA, add projected0, else add edgeA.p1
            // 3) if projected0 is on edgeB, add projected0, else add edgeB.p0
            // 4) if projected1 is on edgeB, add projected1, else add edgeB.p1
            // 5) close the polygon
            Coordinate coord1 = projected1.isOnEdge(edgeA) ? projected1 : edgeA.p0;
            Coordinate coord2 = projected0.isOnEdge(edgeA) ? projected0 : edgeA.p1;
            Coordinate coord3 = projected0.isOnEdge(edgeB) ? projected0 : edgeB.p0;
            Coordinate coord4 = projected1.isOnEdge(edgeB) ? projected1 : edgeB.p1;
            trapezoid = GeometryFactoryUtils.createPolygon(coord1, coord2, coord3, coord4);
            trapezoidArea = trapezoid.getArea();
        }

        public Polygon getTriangle1() {
            return triangle1;
        }

        public Polygon getTrapezoid() {
            return trapezoid;
        }

        public Polygon getTriangle2() {
            return triangle2;
        }

        public double getTotalArea() {
            return triangle1Area + trapezoidArea + triangle2Area;
        }

        /**
         * Produces a a collection of possible cuts located in one of {@link EdgePairSubpolygons}.
         * @param polygon The polygon from which the area should be cut away
         * @param singlePartArea area to cut away
         * @param segmentCountBetweenEdgePair number of line segments between edgeA and edgeB (exclusive)
         * @param segmentCountOutsideEdgePair number of line segments between edgeB and edgeA (exclusive)
         * @return A list of 0, 1 or 2 possible cuts
         */
        public List<Cut> getCuts(Polygon polygon, double singlePartArea, int segmentCountBetweenEdgePair, int segmentCountOutsideEdgePair) {
            List<Cut> cuts = new ArrayList<>(2);

            Polygon polygonOutside1 = null;
            Polygon polygonOutside2 = null;
            double areaOutside1 = polygonOutside1 != null ? polygonOutside1.getArea() : 0;
            double areaOutside2 = polygonOutside2 != null ? polygonOutside2.getArea() : 0;

            // if edges are not connected directly, polygon has extra area adjacent to them

            if (segmentCountBetweenEdgePair > 1) {
                // calculate extra area bounded by segmentsBetweenEdgePair
                polygonOutside1 = GeometryFactoryUtils.slicePolygon(polygon, edgeA.p1, edgeB.p0);
            }
            if (segmentCountOutsideEdgePair > 1) {
                // calculate extra area bounded by segmentsOutsideEdgePair
                polygonOutside2 = GeometryFactoryUtils.slicePolygon(polygon, edgeB.p1, edgeA.p0);
            }

            // check first direction (areaOutside1 + T1 + Trapezoid + T2)
            if (areaOutside1 < singlePartArea) {
                // area outside is smaller than the one we need to cut away (if it is exactly equal, this cut was considered before for a different edge pair)

                // Check if the cut is located in Triangle 1
                if (areaOutside1 + triangle1Area >= singlePartArea) {
                    // produce a Cut in Triangle1

                    double areaToCutAwayInTriangle = singlePartArea - areaOutside1;
                    double fraction = areaToCutAwayInTriangle / triangle1Area;
                    assert fraction >= 0 && fraction <= 1 : "Fraction must be between 0 and 1 (inclusive)";

                    ProjectedVertex projected0 = (ProjectedVertex) triangle1.getCoordinates()[1];
                    LineSegment edgeWithPointOfCut = projected0.isOnEdge(edgeA) ? new LineSegment(edgeA.p1, projected0) : new LineSegment(edgeB.p0, projected0);
                    Coordinate pointOfCut = edgeWithPointOfCut.pointAlong(fraction);
                    LineSegment lineOfCut = GeometryUtils.isPointOnLineSegment(pointOfCut, edgeA) ? new LineSegment(edgeB.p0, pointOfCut) : new LineSegment(edgeA.p1, pointOfCut);

                    // TODO: check if lineOfCut intersects any other lines on exterior ring

                    Polygon triangleToCutAway = GeometryFactoryUtils.createTriangle(edgeA.p1, pointOfCut, edgeB.p0);
                    Polygon cutAwayPolygon = (Polygon) polygonOutside1.union(triangleToCutAway);

                    cuts.add(new Cut(lineOfCut.getLength(), cutAwayPolygon));

                } else if (areaOutside1 + triangle1Area + trapezoidArea >= singlePartArea) {
                    // produce cut in Trapezoid

                    double areaToCutAway = singlePartArea - (areaOutside1 + triangle1Area);
                    double fraction = areaToCutAway / trapezoidArea;
                    assert fraction >= 0 && fraction <= 1 : "Fraction must be between 0 and 1 (inclusive)";

                    Coordinate pointOfCutOnEdgeA = new LineSegment(edgeA.p1, edgeA.p0).pointAlong(fraction);        // point along reversed edgeA
                    Coordinate pointOfCutOnEdgeB = edgeB.pointAlong(fraction);
                    LineSegment lineOfCut = new LineSegment(pointOfCutOnEdgeA, pointOfCutOnEdgeB);

                    // TODO: proceed if not intersects

                    Polygon trapezoidToCutAway = GeometryFactoryUtils.createPolygon(pointOfCutOnEdgeA, edgeA.p1, edgeB.p0, pointOfCutOnEdgeB);

                    // FIXME: this can be improved
                    Polygon cutAwayPolygon = polygonOutside1;
                    if (triangle1Area > 0) {
                        cutAwayPolygon = (Polygon) cutAwayPolygon.union(triangle1);
                    }
                    cutAwayPolygon = (Polygon) cutAwayPolygon.union(trapezoidToCutAway);
                    cuts.add(new Cut(lineOfCut.getLength(), cutAwayPolygon));

                } else if (areaOutside1 + getTotalArea() >= singlePartArea) {
                    // produce cut in Triangle2

                    double areaToCutAwayInTriangle = singlePartArea - (areaOutside1 + triangle1Area + trapezoidArea);
                    double fraction = areaToCutAwayInTriangle / triangle2Area;
                    assert fraction >= 0 && fraction <= 1 : "Fraction must be between 0 and 1 (inclusive)";

                    ProjectedVertex projected1 = (ProjectedVertex) triangle2.getCoordinates()[1];
                    LineSegment edgeWithPointOfCut = projected1.isOnEdge(edgeA) ? new LineSegment(edgeA.p0, projected1) : new LineSegment(edgeB.p1, projected1);
                    Coordinate pointOfCut = edgeWithPointOfCut.pointAlong(fraction);
                    LineSegment lineOfCut = GeometryUtils.isPointOnLineSegment(pointOfCut, edgeA) ? new LineSegment(edgeB.p1, pointOfCut) : new LineSegment(edgeA.p0, pointOfCut);

                    // TODO: check if lineOfCut intersects any other lines on exterior ring

                    Polygon triangleToCutAway = GeometryFactoryUtils.createTriangle(edgeA.p0, pointOfCut, edgeB.p1);

                    // FIXME: this can be improved
                    Polygon cutAwayPolygon = polygonOutside1;
                    if (triangle1Area > 0) {
                        cutAwayPolygon = (Polygon) cutAwayPolygon.union(triangle1);
                    }
                    cutAwayPolygon = (Polygon) cutAwayPolygon.union(trapezoid).union(triangleToCutAway);
                    cuts.add(new Cut(lineOfCut.getLength(), cutAwayPolygon));
                }
            }

            // TODO: check another direction (areaOutside2 + T2 + Trapezoid + T1)

            // sanity check
            if (almostEqual(areaOutside1 + areaOutside2 + getTotalArea(), polygon.getArea())) {
                throw new IllegalStateException();
            }

            return Collections.emptyList();
        }

        private boolean almostEqual(double a, double b) {
            double epsilon = 1e-5;
            return Math.abs(a - b) < epsilon;
        }

        @Override
        public String toString() {
            return "EdgePairSubpolygons [triangle1=" + triangle1 + ", trapezoid=" + trapezoid + ", triangle2=" + triangle2 + "]";
        }
    }
}