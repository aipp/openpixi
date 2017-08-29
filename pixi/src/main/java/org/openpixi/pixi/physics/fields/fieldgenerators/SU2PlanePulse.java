package org.openpixi.pixi.physics.fields.fieldgenerators;

import org.openpixi.pixi.math.*;
import org.openpixi.pixi.physics.Simulation;
import org.openpixi.pixi.physics.grid.Cell;
import org.openpixi.pixi.physics.grid.Grid;

public class SU2PlanePulse implements IFieldGenerator {

	private int numberOfDimensions;
	private int numberOfComponents;
	private double[] direction;
	private double[] position;
	private double[] amplitudeSpatialDirection;
	private double[] amplitudeColorDirection;
	private double amplitudeMagnitude;
	private double sigma;

	private Simulation s;
	private Grid grid;
	private double timeStep;

	public SU2PlanePulse(double[] direction,
						 double[] position,
						 double[] amplitudeSpatialDirection,
						 double[] amplitudeColorDirection,
						 double amplitudeMagnitude,
						 double sigma) {
		this.numberOfDimensions = direction.length;
		this.numberOfComponents = amplitudeColorDirection.length;

		this.direction = direction;
		this.position = position;
		/*
			Amplitude directions should be normalized.
		 */
		this.amplitudeSpatialDirection = this.normalizeVector(amplitudeSpatialDirection);
		this.amplitudeColorDirection = this.normalizeVector(amplitudeColorDirection);
		this.amplitudeMagnitude = amplitudeMagnitude;
		this.sigma = sigma;
	}

	public void applyFieldConfiguration(Simulation s) {
		this.s = s;
		this.grid = s.grid;
		this.timeStep = s.getTimeStep();
		double c = s.getSpeedOfLight();

		ElementFactory factory = grid.getElementFactory();
		int colors = grid.getNumberOfColors();

		/*
			Setup the field amplitude for the plane pulse.
		 */
		AlgebraElement[] amplitudeYMField = new AlgebraElement[this.numberOfDimensions];
		for (int i = 0; i < this.numberOfDimensions; i++) {
			amplitudeYMField[i] = factory.algebraZero(colors);
			for (int j = 0; j < this.numberOfComponents; j++) {
				amplitudeYMField[i].set(j,this.amplitudeMagnitude * this.amplitudeSpatialDirection[i] * this.amplitudeColorDirection[j]);
			}
		}

		int numberOfCells = grid.getTotalNumberOfCells();

		/*
			Cycle through each cell and apply the plane pulse configuration to the links and electric fields.
		 */
		for (int ci = 0; ci < numberOfCells; ci++) {
			int[] cellPosition = grid.getCellPos(ci);
			double[] currentPosition = getPosition(cellPosition);

			double scalarProduct = 0.0;
			for (int i = 0; i < this.numberOfDimensions; i++) {
				scalarProduct += this.direction[i] * (currentPosition[i] - this.position[i]);
			}

			// Multiplicative factor for the plane pulse at t = 0 (for electric fields)
			double phaseE = scalarProduct;
			double electricFieldFactor = -c * phaseE / Math.pow(sigma, 2.0) *
					Math.exp(-Math.pow(phaseE / this.sigma, 2.0) / 2.0);
			// Multiplicative factor for the plane pulse at t = - dt/2 (for links)
			double phaseU = scalarProduct + c * timeStep / 2.0;
			double gaugeFieldFactor = Math.exp(-Math.pow(phaseU / this.sigma, 2.0) / 2.0);


			Cell currentCell = grid.getCell(ci);

			for (int i = 0; i < this.numberOfDimensions; i++) {
				double unitFactor = s.grid.getLatticeUnitFactor(i);
				//Setup the gauge links
				GroupElement U = currentCell.getU(i).mult(amplitudeYMField[i].mult(gaugeFieldFactor * unitFactor).getLink());
				currentCell.setU(i, U);

				//Setup the electric fields
				currentCell.addE(i, amplitudeYMField[i].mult(electricFieldFactor * unitFactor));
			}
		}

	}

	private double[] normalizeVector(double[] vector) {
		double norm = 0.0;
		double[] output = new double[vector.length];
		for (int i = 0; i < vector.length; i++) {
			norm += vector[i] * vector[i];
		}
		norm = Math.sqrt(norm);
		for (int i = 0; i < vector.length; i++) {
			output[i] = vector[i] / norm;
		}
		return output;
	}

	private double[] getPosition(int[] cellPosition) {
		double[] position = new double[this.numberOfDimensions];
		for (int i = 0; i < this.numberOfDimensions; i++) {
			position[i] = cellPosition[i] * grid.getLatticeSpacing(i);
		}
		return position;
	}
}
