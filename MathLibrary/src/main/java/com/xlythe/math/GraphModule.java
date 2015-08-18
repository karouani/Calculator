package com.xlythe.math;

import android.os.AsyncTask;

import org.javia.arity.SyntaxException;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class GraphModule extends Module {
    private static final String X = "X";
    private static final String Y = "Y";
    private float mMinY;
    private float mMaxY;
    private float mMinX;
    private float mMaxX;
    private float mZoomLevel = 1f;

    public GraphModule(Solver solver) {
        super(solver);
    }

    public void setRange(float min, float max) {
        mMinY = min;
        mMaxY = max;
    }

    public void setDomain(float min, float max) {
        mMinX = min;
        mMaxX = max;
    }

    public void setZoomLevel(float level) {
        mZoomLevel = level;
    }

    /**
     * Given a function, updateGraph will attempt to build a list of points that can be graphed.
     * */
    public AsyncTask updateGraph(String text, OnGraphUpdatedListener l) {
        boolean endsWithOperator = text.length() != 0 &&
                (Solver.isOperator(text.charAt(text.length() - 1)) || text.endsWith("("));
        boolean containsMatrices = getSolver().displayContainsMatrices(text);
        boolean domainNotSet = mMinX == mMaxX;
        if(endsWithOperator || containsMatrices || domainNotSet) {
            return null;
        }

        GraphTask newTask = new GraphTask(getSolver(), mMinY, mMaxY, mMinX, mMaxX, mZoomLevel, l);
        newTask.execute(text);
        return newTask;
    }

    class GraphTask extends AsyncTask<String, String, List<Point>> {
        private final Solver mSolver;
        private final OnGraphUpdatedListener mListener;
        private final float mMinY;
        private final float mMaxY;
        private final float mMinX;
        private final float mMaxX;
        private final float mZoomLevel;

        public GraphTask(Solver solver, float minY, float maxY, float minX, float maxX,
                         float zoomLevel, OnGraphUpdatedListener l) {
            mSolver = solver;
            mListener = l;
            mMinY = minY;
            mMaxY = maxY;
            mMinX = minX;
            mMaxX = maxX;
            mZoomLevel = zoomLevel;
        }

        @Override
        protected List<Point> doInBackground(String... eq) {
            String[] equations = eq[0].split("=");
            try {
                if (equations.length >= 2) {
                    String leftEquation = mSolver.getBaseModule().changeBase(equations[0],
                            mSolver.getBaseModule().getBase(), Base.DECIMAL);
                    String rightEquation = mSolver.getBaseModule().changeBase(equations[1],
                            mSolver.getBaseModule().getBase(), Base.DECIMAL);
                    return graph(leftEquation, rightEquation);
                } else {
                    String equation = mSolver.getBaseModule().changeBase(eq[0],
                            mSolver.getBaseModule().getBase(), Base.DECIMAL);
                    return graph(equation);
                }
            } catch(SyntaxException e) {
                cancel(true);
                return null;
            }
        }

        public List<Point> graph(String equation) {
            final LinkedList<Point> series = new LinkedList<Point>();
            mSolver.pushFrame();

            final float delta = 0.1f * mZoomLevel;
            for(float x = mMinX; x <= mMaxX; x += delta) {
                if(isCancelled()) {
                    return null;
                }

                try {
                    mSolver.define(X, x);
                    float y = (float) mSolver.eval(equation);
                    series.add(new Point(x, y));
                } catch(SyntaxException e) {}
            }
            mSolver.popFrame();

            return Collections.unmodifiableList(series);
        }

        public List<Point> graph(String leftEquation, String rightEquation) {
            final LinkedList<Point> series = new LinkedList<Point>();
            mSolver.pushFrame();

            final float delta = 0.1f * mZoomLevel;
            if(leftEquation.equals(Y) && !rightEquation.contains(Y)) {
                for(float x = mMinX; x <= mMaxX; x += delta) {
                    if(isCancelled()) {
                        return null;
                    }

                    try {
                        mSolver.define(X, x);
                        float y = (float) mSolver.eval(rightEquation);
                        series.add(new Point(x, y));
                    } catch(SyntaxException e) {}
                }
            } else if(leftEquation.equals(X) && !rightEquation.contains(X)) {
                for(float y = mMinY; y <= mMaxY; y += delta) {
                    if(isCancelled()) {
                        return null;
                    }

                    try {
                        mSolver.define(Y, y);
                        float x = (float) mSolver.eval(rightEquation);
                        series.add(new Point(x, y));
                    } catch(SyntaxException e) {}
                }
            } else if(rightEquation.equals(Y) && !leftEquation.contains(Y)) {
                for(float x = mMinX; x <= mMaxX; x += delta) {
                    if(isCancelled()) {
                        return null;
                    }

                    try {
                        mSolver.define(X, x);
                        float y = (float) mSolver.eval(leftEquation);
                        series.add(new Point(x, y));
                    } catch(SyntaxException e) {}
                }
            } else if(rightEquation.equals(X) && !leftEquation.contains(X)) {
                for(float y = mMinY; y <= mMaxY; y += delta) {
                    if(isCancelled()) {
                        return null;
                    }

                    try {
                        mSolver.define(Y, y);
                        float x = (float) mSolver.eval(leftEquation);
                        series.add(new Point(x, y));
                    } catch(SyntaxException e) {}
                }
            } else {
                for(float x = mMinX; x <= mMaxX; x += 0.2f * mZoomLevel) {
                    for(float y = mMaxY; y >= mMinY; y -= 0.2f * mZoomLevel) {
                        if(isCancelled()) {
                            return null;
                        }

                        try {
                            mSolver.define(X, x);
                            mSolver.define(Y, y);
                            float leftSide = (float) mSolver.eval(leftEquation);
                            float rightSide = (float) mSolver.eval(rightEquation);
                            if(leftSide < 0 && rightSide < 0) {
                                if(leftSide * 0.98f >= rightSide && leftSide * 1.02f <= rightSide) {
                                    series.add(new Point(x, y));
                                }
                            } else {
                                if(leftSide * 0.98 <= rightSide && leftSide * 1.02 >= rightSide) {
                                    series.add(new Point(x, y));
                                }
                            }
                        } catch(SyntaxException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            mSolver.popFrame();

            return Collections.unmodifiableList(series);
        }

        @Override
        protected void onPostExecute(List<Point> result) {
            mListener.onGraphUpdated(result);
        }
    }

    public static interface OnGraphUpdatedListener {
        public void onGraphUpdated(List<Point> result);
    }
}
