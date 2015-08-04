package org.openpixi.pixi.ui.panel;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.Box;
import org.openpixi.pixi.physics.Simulation;
import org.openpixi.pixi.physics.gauge.CoulombGauge;
import org.openpixi.pixi.physics.gauge.RandomGauge;
import org.openpixi.pixi.physics.grid.Grid;
import org.openpixi.pixi.physics.particles.IParticle;
import org.openpixi.pixi.ui.SimulationAnimation;
import org.openpixi.pixi.ui.panel.properties.ColorProperties;
import org.openpixi.pixi.ui.panel.properties.GaugeProperties;
import org.openpixi.pixi.ui.panel.properties.ScaleProperties;

/**
 * This panel shows the one-dimensional electric field along the x-direction.
 * Several field lines for various grid positions along the y-direction are
 * superimposed.
 */
public class ElectricFieldPanel extends AnimationPanel {

	ColorProperties colorProperties;
	ScaleProperties scaleProperties;
	GaugeProperties gaugeProperties;

	/** Constructor */
	public ElectricFieldPanel(SimulationAnimation simulationAnimation) {
		super(simulationAnimation);
		colorProperties = new ColorProperties(simulationAnimation);
		scaleProperties = new ScaleProperties(simulationAnimation);
		gaugeProperties = new GaugeProperties(simulationAnimation);
	}

	/** Display the particles */
	public void paintComponent(Graphics graph1) {
		Graphics2D graph = (Graphics2D) graph1;
		setBackground(Color.white);

		/*
			Note:
			These two commands set the origin of the coordinate system to the lower-left corner of the panel and flip
			the y-axis. The system is now as follows:
			Let (x,y0 denote a point on the panel. (0,0) is the lower-left corner. The x-component increases to the
			right. The y-component increases downwards.
		 */
		graph.translate(0, this.getHeight());
		graph.scale(1, -1);

		super.paintComponent(graph1);

		Simulation s = getSimulationAnimation().getSimulation();
		/** Scaling factor for the displayed panel in x-direction*/
		double sx = getWidth() / s.getWidth();
		/** Scaling factor for the displayed panel in y-direction*/
		double sy = getHeight() / s.getHeight();

		double panelHeight = getHeight();

		// Lattice spacing and coupling constant
		double as = s.grid.getLatticeSpacing();
		double g = s.getCouplingConstant();

		int colorIndex = colorProperties.getColorIndex();
		int directionIndex = colorProperties.getDirectionIndex();

		boolean useCoulombGauge = gaugeProperties.isCoulombGauge();
		Grid drawGrid = s.grid;
		if (useCoulombGauge) {
			CoulombGauge coulombGauge = new CoulombGauge(s.grid);
			Grid gridCopy = new Grid(s.grid);
			coulombGauge.applyGaugeTransformation(gridCopy);
			drawGrid = gridCopy;

//			// Test random gauge:
//			RandomGauge randomGauge = new RandomGauge(s.grid);
//			randomGauge.setRandomVector(new double[] {0, 1, 0});
//			Grid gridCopy = new Grid(s.grid);
//			randomGauge.applyGaugeTransformation(gridCopy);
//			drawGrid = gridCopy;
		}

		// Draw particles on a central line:
		for (int i = 0; i < s.particles.size(); i++) {
			IParticle par = s.particles.get(i);
			graph.setColor(par.getColor());
			double radius = par.getRadius();
			int width = (int) (2*sx*radius);
			int height = (int) (2*sx*radius);
			if(width > 2 && height > 2) {
				graph.fillOval((int) (par.getPosition(0)*sx) - width/2, (int) (panelHeight/2 - height/2),  width,  height);
			}
			else {
				graph.drawRect((int) (par.getPosition(0)*sx), (int) panelHeight/2, 0, 0);
			}
		}

		// Draw electric field:
		graph.setColor(Color.black);
		// Scale factor for electric field
		double scaleE = scaleProperties.getScale();

		// Scale factor for gauge field
		double scaleA = scaleProperties.getScale();

		scaleProperties.resetAutomaticScale();

		int[] pos = new int[s.getNumberOfDimensions()];
		for(int w = 2; w < s.getNumberOfDimensions(); w++) {
			pos[w] = s.grid.getNumCells(w)/2;
		}
		
		for(int k = 0; k < s.grid.getNumCells(1); k++)
		{
			int newPosition = 0;
			int newValue = 0;
			for(int i = 0; i < s.grid.getNumCells(0); i++)
			{

				int oldPosition = newPosition;
				int oldValue = newValue;
				pos[0] = i;
				pos[1] = k;

				// Electric fields are placed at the lattice points.
				newPosition = (int) (s.grid.getLatticeSpacing() * (i) * sx);

				/*
					Expectation: Positive fields should point upwards.
					In the flipped and translated coordinate system defined above we have to add the fields to the
					center of the panel in order to get the expected result.
				*/
				double electricField = drawGrid.getE(s.grid.getCellIndex(pos), directionIndex).get(colorIndex) / (as * g);
				scaleProperties.putValue(electricField);
				newValue = (int) (((0.5 + scaleE * electricField) * panelHeight));

				if (i > 0) {
					graph.drawLine(oldPosition, oldValue,newPosition, newValue);
				}
			}
		}
		
		// Draw gauge field:
		graph.setColor(Color.green);
				
		for(int k = 0; k < s.grid.getNumCells(1); k++)
		{
			int newPosition = 0;
			int newValue = 0;
			for(int i = 0; i < s.grid.getNumCells(0); i++)
			{

				int oldPosition = newPosition;
				int oldValue = newValue;
				pos[0] = i;
				pos[1] = k;

				// Gauge fields are placed at the lattice points.
				newPosition = (int) (s.grid.getLatticeSpacing() * i * sx);

				/*
					Expectation: Positive fields should point upwards.
					In the flipped and translated coordinate system defined above we have to add the fields to the
					center of the panel in order to get the expected result.
				*/
				double gaugeField = drawGrid.getU(s.grid.getCellIndex(pos), directionIndex).proj().get(colorIndex) / (as * g);
				scaleProperties.putValue(gaugeField);
				newValue = (int) (((0.5 + scaleA * gaugeField) * panelHeight));

				if (i > 0) {
					graph.drawLine(oldPosition, oldValue,newPosition, newValue);
				}
			}
		}
		scaleProperties.calculateAutomaticScale(0.5);

	}

	public void addPropertyComponents(Box box) {
		addLabel(box, "Electric field panel");
		colorProperties.addComponents(box);
		scaleProperties.addComponents(box);
		gaugeProperties.addComponents(box);
	}

	public ColorProperties getColorProperties() {
		return colorProperties;
	}

	public ScaleProperties getScaleProperties() {
		return scaleProperties;
	}

	public GaugeProperties getGaugeProperties() {
		return gaugeProperties;
	}
}
