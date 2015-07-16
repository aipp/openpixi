package org.openpixi.pixi.ui.panel.chart;

import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.axis.AAxis;
import info.monitorenter.gui.chart.axis.AxisLinear;
import info.monitorenter.gui.chart.axis.scalepolicy.AxisScalePolicyTransformation;
import info.monitorenter.gui.chart.labelformatters.LabelFormatterNumber;
import info.monitorenter.gui.chart.labelformatters.LabelFormatterSimple;
import info.monitorenter.gui.chart.traces.Trace2DLtd;

import java.awt.Color;
import java.text.DecimalFormat;

import javax.swing.Box;

import org.openpixi.pixi.physics.Simulation;
import org.openpixi.pixi.physics.measurements.FieldMeasurements;
import org.openpixi.pixi.ui.SimulationAnimation;
import org.openpixi.pixi.ui.panel.properties.BooleanProperties;

/**
 * This panel shows various charts.
 */
public class Chart2DPanel extends AnimationChart2DPanel {

	ITrace2D[] traces;

	public final int INDEX_GAUSS_VIOLATION = 0;
	public final int INDEX_E_SQUARED = 1;
	public final int INDEX_B_SQUARED = 2;

	String[] chartLabel = new String[] {
			"Gauss law violation",
			"E squared",
			"B squared"
	};

	Color[] traceColors = new Color[] {
			Color.red,
			Color.blue,
			Color.green
	};

	public BooleanProperties logarithmicProperty;
	public BooleanProperties[] chartContentProperty;

	private boolean oldLogarithmicValue = false;

	private FieldMeasurements fieldMeasurements;

	/** Constructor */
	public Chart2DPanel(SimulationAnimation simulationAnimation) {
		super(simulationAnimation);

		traces = new ITrace2D[chartLabel.length];
		for (int i = 0; i < chartLabel.length; i++) {
			// TODO: Set buffer size according to simulation duration:
			traces[i] = new Trace2DLtd(2000);
			traces[i].setColor(traceColors[i]);
			traces[i].setName(chartLabel[i]);
			addTrace(traces[i]);
		}

		this.fieldMeasurements = new FieldMeasurements();

		logarithmicProperty = new BooleanProperties("Logarithmic scale", false);

		chartContentProperty = new BooleanProperties[chartLabel.length];
		for (int i = 0; i < chartLabel.length; i++) {
			chartContentProperty[i] = new BooleanProperties(chartLabel[i], false);
		}
	}

	public void update() {
		if (logarithmicProperty.getValue() != oldLogarithmicValue) {
			oldLogarithmicValue = logarithmicProperty.getValue();
			if (oldLogarithmicValue) {
				// Logarithmic scale
				AAxis<?> axisy = new MyAxisLog10<AxisScalePolicyTransformation>(
						new LabelFormatterNumber(new DecimalFormat("0.0E0")),
						new MyAxisScalePolicyTransformation());
				setAxisYLeft(axisy, 0);
			} else {
				// Linear scale
				AAxis<?> axisy = new AxisLinear<AxisScalePolicyTransformation>();
				setAxisYLeft(axisy, 0);
			}
		}

		Simulation s = getSimulationAnimation().getSimulation();
		double time = s.totalSimulationTime;

		//TODO Make this method d-dimensional!!
		double[] esquares = new double[3];
		double[] bsquares = new double[3];
		for (int i = 0; i < 3; i++) {
			esquares[i] = fieldMeasurements.calculateEsquared(s.grid, i);
			bsquares[i] = fieldMeasurements.calculateBsquared(s.grid, i);
		}

		double eSquared = esquares[0] + esquares[1] + esquares[2];
		double bSquared = bsquares[0] + bsquares[1] + bsquares[2];
		double px = -esquares[0] + esquares[1] + esquares[2] - bsquares[0] + bsquares[1] + bsquares[2];
		double py = +esquares[0] - esquares[1] + esquares[2] + bsquares[0] - bsquares[1] + bsquares[2];
		double pz = +esquares[0] + esquares[1] - esquares[2] + bsquares[0] + bsquares[1] - bsquares[2];

		double gaussViolation = fieldMeasurements.calculateGaussConstraint(s.grid);

		traces[INDEX_E_SQUARED].addPoint(time, eSquared);
		traces[INDEX_B_SQUARED].addPoint(time, bSquared);
		traces[INDEX_GAUSS_VIOLATION].addPoint(time, gaussViolation);

		for (int i = 0; i < chartContentProperty.length; i++) {
			traces[i].setVisible(chartContentProperty[i].getValue());
		}
	}

	public void clear() {
		for (int i = 0; i < chartContentProperty.length; i++) {
			traces[i].removeAllPoints();
		}
	}

	public void addPropertyComponents(Box box) {
		addLabel(box, "Chart panel");
		logarithmicProperty.addComponents(box);
		for (BooleanProperties b : chartContentProperty) {
			b.addComponents(box);
		}
	}
}