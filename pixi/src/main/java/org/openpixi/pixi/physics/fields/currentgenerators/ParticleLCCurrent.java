package org.openpixi.pixi.physics.fields.currentgenerators;

import org.openpixi.pixi.math.AlgebraElement;
import org.openpixi.pixi.math.GroupElement;
import org.openpixi.pixi.physics.Simulation;
import org.openpixi.pixi.physics.fields.NewLCPoissonSolver;
import org.openpixi.pixi.physics.util.GridFunctions;

import java.util.ArrayList;

/**
 * This current generator uses particles on fixed trajectories to correctly interpolate the charge and current density
 * on the grid according to CGC initial conditions.
 */
public class ParticleLCCurrent implements ICurrentGenerator {

	/**
	 * Direction of movement of the charge density. Values range from 0 to numberOfDimensions-1.
	 */
	private int direction;

	/**
	 * Orientation of movement. Values are -1 or 1.
	 */
	private int orientation;

	/**
	 * Longitudinal location of the initial charge density in the simulation box.
	 */
	private double location;

	/**
	 * Longitudinal width of the charge density.
	 */
	private double longitudinalWidth;

	/**
	 * List of point charges to use as intial conditions.
	 */
	private ArrayList<PointCharge> charges;

	/**
	 * Array containing the size of the transversal grid.
	 */
	private int[] transversalNumCells;

	/**
	 * Transversal charge density.
	 */
	private AlgebraElement[] transversalChargeDensity;

	/**
	 * Total number of cells in the transversal grid.
	 */
	private int totalTransversalCells;

	/**
	 * Number of colors used in the simulation.
	 */
	private int numberOfColors;

	/**
	 * Number of components associated with the number of colors Nc. For Nc > 1 it is Nc^2-1.
	 */
	private int numberOfComponents;

	/**
	 * Lattice spacing of the grid.
	 */
	private double as;

	/**
	 * Time step used in the simulation.
	 */
	private double at;

	/**
	 * Coupling constant used in the simulation.
	 */
	private double g;

	/**
	 * List of particles which sample the charge distributions. The charges of these particles are evolved and interpolated consistently according to the field.
	 */
	private ArrayList<Particle> particles;

	/**
	 * Poisson solver for solving the CGC intitial conditions.
	 */
	NewLCPoissonSolver poissonSolver;

	/**
	 * Standard constructor for the ParticleLCCurrent class.
	 *
	 * @param direction Direction of the transversal charge density movement.
	 * @param orientation Orientation fo the transversal charge density movement.
	 * @param location Longitudinal starting location.
	 * @param longitudinalWidth Longitudinal width of the Gaussian shape for the charge density.
	 */
	public ParticleLCCurrent(int direction, int orientation, double location, double longitudinalWidth){
		this.direction = direction;
		this.orientation = orientation;
		this.location = location;
		this.longitudinalWidth = longitudinalWidth;
		this.charges = new ArrayList<PointCharge>();
	}

	/**
	 * Adds a point charge at a certain position. This is mainly used to specify initial conditions.
	 *
	 * @param location
	 * @param colorDirection
	 * @param magnitude
	 */
	public void addCharge(double[] location, double[] colorDirection, double magnitude) {
		// This method should be called from the YAML object to add the charges for the current generator.
		this.charges.add(new PointCharge(location, colorDirection, magnitude));
	}

	/**
	 * Sets the intitial transversal charge density.
	 *
	 * @param transversalChargeDensity
	 */
	public void setTransversalChargeDensity(AlgebraElement[] transversalChargeDensity) {
		this.transversalChargeDensity = transversalChargeDensity;
	}

	/**
	 * Initializes the fields, charges and currents on the grid.
	 *
	 * @param s
	 * @param totalInstances
	 */
	public void initializeCurrent(Simulation s, int totalInstances) {
		// 0) Define some variables.
		numberOfColors = s.getNumberOfColors();
		numberOfComponents = s.grid.getElementFactory().numberOfComponents;
		as = s.grid.getLatticeSpacing();
		at = s.getTimeStep();
		g = s.getCouplingConstant();

		// 1) Initialize transversal charge density grid using the charges array.
		transversalNumCells = GridFunctions.reduceGridPos(s.grid.getNumCells(), direction);
		totalTransversalCells = GridFunctions.getTotalNumberOfCells(transversalNumCells);
		transversalChargeDensity = new AlgebraElement[totalTransversalCells];
		for (int i = 0; i < totalTransversalCells; i++) {
			transversalChargeDensity[i] = s.grid.getElementFactory().algebraZero();
		}

		// Iterate over (point) charges, round them to the nearest grid point and add them to the transversal charge density.
		for (int i = 0; i < charges.size(); i++) {
			PointCharge c = charges.get(i);
			AlgebraElement chargeAmplitude = s.grid.getElementFactory().algebraZero(s.getNumberOfColors());
			for (int j = 0; j < numberOfComponents; j++) {
				chargeAmplitude.set(j, c.colorDirection[j] * c.magnitude / Math.pow(as, s.getNumberOfDimensions() - 1));
			}
			int[] gridPos = GridFunctions.nearestGridPoint(c.location, as);
			interpolateChargeToGridCIC(s, c.location, chargeAmplitude);
			//interpolateChargeToGridNGP(s, c.location, chargeAmplitude);
		}

		// 1b) Remove monopole and dipole moment.
		// removeMonopoleMoment(s);
		// removeDipoleMoment(s);

		// 2) Initialize the NewLCPoissonSolver with the transversal charge density and solve for the fields U and E.
		poissonSolver = new NewLCPoissonSolver(direction, orientation, location, longitudinalWidth,
				transversalChargeDensity, transversalNumCells);
		poissonSolver.initialize(s);
		poissonSolver.solve(s);


		// 3) Interpolate grid charge and current density.
		initializeParticles(s, 1);
		applyCurrent(s);

		// You're done: charge density, current density and the fields are set up correctly.
	}


	/**
	 * Interpolates and evolves the charges and currents to the grid.
	 * @param s
	 */
	public void applyCurrent(Simulation s) {
		evolveCharges(s);
		removeParticles(s);
		interpolateChargesAndCurrents(s);
	}

	/**
	 * Interpolates a point charge to the transversal grid. The charge is smeared according to the cloud-in-cell shape.
	 *
	 * @param s
	 * @param chargePosition
	 * @param charge
	 */
	private void interpolateChargeToGridCIC(Simulation s, double[] chargePosition, AlgebraElement charge) {

		int effNumberOfDimensions = transversalNumCells.length;

		// Add all relevant points of the hypercube defining the cell.
		ArrayList<int[]> listOfPoints = new ArrayList<int[]>();
		int[] gridPos0 = GridFunctions.flooredGridPoint(chargePosition, as);
		listOfPoints.add(gridPos0);
		for (int i = 0; i < effNumberOfDimensions; i++) {
			ArrayList<int[]> newPoints = new ArrayList<int[]>();
			for (int j = 0; j < listOfPoints.size(); j++) {
				int[] currentPoint = listOfPoints.get(j).clone();
				currentPoint[i] += 1;
				newPoints.add(currentPoint);
			}
			listOfPoints.addAll(newPoints);
		}

		// Interpolate to each grid point of the hypercube.
		double total = 0.0;
		for(int[] p : listOfPoints) {
			double weigth = 1.0;
			for (int i = 0; i < effNumberOfDimensions; i++) {
				double dist = 1.0 - Math.abs(p[i] - chargePosition[i] / as);
				weigth *= dist;
			}
			total += weigth;
			transversalChargeDensity[GridFunctions.getCellIndex(p, transversalNumCells)].addAssign(charge.mult(weigth));
		}
		System.out.println(total);
	}

	/**
	 * Interpolates a point charge to the transversal grid. The charge is "smeared" (not really) according to the nearest-grid-point method.
	 *
	 * @param s
	 * @param chargePosition
	 * @param charge
	 */
	private void interpolateChargeToGridNGP(Simulation s, double[] chargePosition, AlgebraElement charge) {
		int[] gridPos0 = GridFunctions.nearestGridPoint(chargePosition, as);
		transversalChargeDensity[GridFunctions.getCellIndex(gridPos0, transversalNumCells)].addAssign(charge);
	}

	/**
	 * Removes the monopole moment by subtracting a constant charge at each lattice site of the transversal charge density.
	 * This is not a good way to do this, so make sure the initial conditions are colorless at initialization.
	 *
	 * @param s
	 */
	private void removeMonopoleMoment(Simulation s) {
		AlgebraElement totalCharge = computeTotalCharge(s);
		totalCharge  = totalCharge.mult(1.0 / totalTransversalCells);
		for (int i = 0; i < totalTransversalCells; i++) {
			transversalChargeDensity[i].sub(totalCharge);
		}
	}

	/**
	 * Removes the dipole moment by adding dipoles for each color component. These dipoles cancel the total dipole moment.
	 *
	 * @param s
	 */
	private void removeDipoleMoment(Simulation s) {
		for (int c = 0; c < numberOfComponents; c++) {
			// Position of dipole
			double[] centerOfAbsCharge = computeCenterOfAbsCharge(c);
			// Distance of dipole charges
			double averageDist = computeAverageDistance(s, c);

			double dipoleCharge = 0.0;
			double[] dipoleVector = new double[transversalNumCells.length];
			for (int i = 0; i < totalTransversalCells; i++) {
				int[] gridPos = GridFunctions.getCellPos(i, transversalNumCells);
				double charge = transversalChargeDensity[i].get(c);
				double dist = 0.0;
				for (int j = 0; j < transversalNumCells.length; j++) {
					dist += Math.pow(gridPos[j] * as - centerOfAbsCharge[j], 2);
					dipoleVector[j] += charge * (gridPos[j] * as - centerOfAbsCharge[j]);
				}
				dist = Math.sqrt(dist);
				dipoleCharge += charge * dist / averageDist;
			}
			for (int j = 0; j < transversalNumCells.length; j++) {
				dipoleVector[j] /= dipoleCharge * averageDist;
			}

			// Add two charges to cancel dipole moment at center of abs. charge
			double[] dipoleChargePos1 = centerOfAbsCharge.clone();
			double[] dipoleChargePos2 = centerOfAbsCharge.clone();

			for (int j = 0; j < transversalNumCells.length; j++) {
				dipoleChargePos1[j] += dipoleVector[j] * averageDist / 2.0;
				dipoleChargePos2[j] -= dipoleVector[j] * averageDist / 2.0;
			}

			// Charges of the dipoles
			AlgebraElement dipoleCharge1 = s.grid.getElementFactory().algebraZero();
			AlgebraElement dipoleCharge2 = s.grid.getElementFactory().algebraZero();
			dipoleCharge1.set(c, -dipoleCharge);
			dipoleCharge2.set(c, dipoleCharge);

			interpolateChargeToGridCIC(s, dipoleChargePos1, dipoleCharge1);
			interpolateChargeToGridCIC(s, dipoleChargePos2, dipoleCharge2);
		}
	}

	/**
	 * Computes the total charge on the transversal lattice.
	 *
	 * @param s
	 * @return
	 */
	private AlgebraElement computeTotalCharge(Simulation s) {
		AlgebraElement totalCharge =  s.grid.getElementFactory().algebraZero();
		for (int i = 0; i < totalTransversalCells; i++) {
			totalCharge.addAssign(transversalChargeDensity[i]);
		}
		return totalCharge;
	}

	/**
	 * Computes the "center of charge" for a given component on the transversal lattice. The center is computed from the
	 * absolute values of the charges.
	 *
	 * @param component
	 * @return
	 */
	private double[] computeCenterOfAbsCharge(int component) {
		double[] center = new double[transversalNumCells.length];
		for (int j = 0; j < transversalNumCells.length; j++) {
			center[j] = 0.0;
		}

		double totalCharge = 0.0;
		for (int i = 0; i < totalTransversalCells; i++) {
			int[] gridPos = GridFunctions.getCellPos(i, transversalNumCells);
			double charge = Math.abs(transversalChargeDensity[i].get(component));
			totalCharge += charge;
			for (int j = 0; j < transversalNumCells.length; j++) {
				center[j] += charge * gridPos[j] * as;
			}
		}

		for (int j = 0; j < transversalNumCells.length; j++) {
			center[j] /= totalCharge;
		}

		return center;
	}

	/**
	 * Computes the average (weighted) distance of the charges of a certain component to the center of charge.
	 * This can be used to estimate the size of the charge distribution.
	 *
	 * @param s
	 * @param component
	 * @return
	 */
	private double computeAverageDistance(Simulation s, int component) {
		double averageDistance = 0.0;
		double[] centerOfCharge = computeCenterOfAbsCharge(component);
		double totalAbsCharge = 0.0;
		for (int i = 0; i < totalTransversalCells; i++) {
			int[] gridPos = GridFunctions.getCellPos(i, transversalNumCells);
			double charge = Math.abs(transversalChargeDensity[i].get(component));
			totalAbsCharge += charge;
			double dist = 0.0;
			for (int j = 0; j < transversalNumCells.length; j++) {
				dist += Math.pow(gridPos[j] * as - centerOfCharge[j], 2);
			}
			averageDistance += charge * Math.sqrt(dist);
		}
		return averageDistance / totalAbsCharge;
	}

	/**
	 * Computes the center of charge using the invariant charge (tr(Q^2))^0.5.
	 *
	 * @return
	 */
	private double[] computeCenterOfInvariantCharge() {
		double[] center = new double[transversalNumCells.length];
		for (int j = 0; j < transversalNumCells.length; j++) {
			center[j] = 0.0;
		}

		double totalInvCharge = 0.0;
		for (int i = 0; i < totalTransversalCells; i++) {
			int[] gridPos = GridFunctions.getCellPos(i, transversalNumCells);
			double invCharge = Math.sqrt(transversalChargeDensity[i].square());
			totalInvCharge += invCharge;
			for (int j = 0; j < transversalNumCells.length; j++) {
				center[j] += invCharge * gridPos[j] * as;
			}
		}

		for (int j = 0; j < transversalNumCells.length; j++) {
			center[j] /= totalInvCharge;
		}

		return center;
	}


	/**
	 * Initializes the particles according to the field initial conditions. The charge density is computed from the Gauss law
	 * violations of the initial fields. The particles are then sampled from this charge density.
	 *
	 * @param s
	 * @param particlesPerLink
	 */
	private void initializeParticles(Simulation s, int particlesPerLink) {
		particles = new ArrayList<Particle>();

		// Traverse through charge density and add particles by sampling the charge distribution
		double t0 = - 2*at;
		double prefactor = g * as;
		for (int i = 0; i < s.grid.getTotalNumberOfCells(); i++) {
			AlgebraElement charge = poissonSolver.getGaussConstraint(i);
			int[] gridPos = s.grid.getCellPos(i);
			double dz = 0.0;
			// Particle position
			double[] particlePosition0 = new double[gridPos.length];
			double[] particlePosition1 = new double[gridPos.length];
			for (int k = 0; k < gridPos.length; k++) {
				particlePosition0[k] = gridPos[k] * as;
				particlePosition1[k] = gridPos[k] * as ;
				if(k == direction) {
					particlePosition0[k] += t0 * orientation + dz;
					particlePosition1[k] += (t0 + at) * orientation + dz;
				}
			}

			// Particle velocity
			double[] particleVelocity = new double[gridPos.length];
			for (int k = 0; k < gridPos.length; k++) {
				if(k == direction) {
					particleVelocity[k] = 1.0 * orientation;
				} else {
					particleVelocity[k] = 0.0;
				}
			}

			// Create particle instance and add to particle array.
			if(charge.square() > 10E-18 * prefactor) {
				Particle p = new Particle();
				p.pos0 = particlePosition0;
				p.pos1 = particlePosition1;
				p.vel = particleVelocity;
				p.Q0 = charge;
				p.Q1 = charge.copy();

				particles.add(p);
			}
		}
	}

	/**
	 * Evolves the particle positions and charges according to the Wong equations.
	 *
	 * @param s
	 */
	private void evolveCharges(Simulation s) {
		for(Particle p : particles) {
			// swap variables for charge and position
			p.swap();
			// move particle position according to velocity
			p.move(at);

			// Evolve particle charges
			// check if one cell or two cell move
			int longitudinalIndexOld = (int) (p.pos0[direction] / as);
			int longitudinalIndexNew = (int) (p.pos1[direction] / as);


			if(longitudinalIndexOld == longitudinalIndexNew) {
				// one cell move
				int cellIndexNew = s.grid.getCellIndex(GridFunctions.flooredGridPoint(p.pos0, as));
				double d = Math.abs(p.vel[direction] * at / as);
				GroupElement U;
				if(p.vel[direction] > 0) {
					U = s.grid.getU(cellIndexNew, direction).getAlgebraElement().mult(d).getLink();
				} else {
					U = s.grid.getU(cellIndexNew, direction).getAlgebraElement().mult(d).getLink().adj();
				}
				p.evolve(U);
			} else {
				// two cell move
				int cellIndexOld = s.grid.getCellIndex(GridFunctions.flooredGridPoint(p.pos0, as));
				int cellIndexNew = s.grid.getCellIndex(GridFunctions.flooredGridPoint(p.pos1, as));

				if(longitudinalIndexOld < longitudinalIndexNew) {
					// right move
					// path is split into two parts
					double d0 = Math.abs(longitudinalIndexNew - p.pos0[direction] / as);
					double d1 = Math.abs(longitudinalIndexNew - p.pos1[direction] / as);

					GroupElement U0 = s.grid.getU(cellIndexOld, direction).getAlgebraElement().mult(d0).getLink();
					GroupElement U1 = s.grid.getU(cellIndexNew, direction).getAlgebraElement().mult(d1).getLink();
					GroupElement U = U0.mult(U1);

					p.evolve(U);
				} else {
					// left move
					// path is split into two parts
					double d0 = Math.abs(longitudinalIndexOld - p.pos0[direction] / as);
					double d1 = Math.abs(longitudinalIndexOld - p.pos1[direction] / as);

					GroupElement U0 = s.grid.getU(cellIndexOld, direction).getAlgebraElement().mult(d0).getLink();
					GroupElement U1 = s.grid.getU(cellIndexNew, direction).getAlgebraElement().mult(d1).getLink();
					GroupElement U = U0.mult(U1);

					p.evolve(U.adj());
				}
			}

		}
	}

	/**
	 * Checks if particles have left the simulation box and removes them if necessary.
	 *
	 * @param s
	 */
	private void removeParticles(Simulation s) {
		// Remove particles which have left the simulation box.
		ArrayList<Particle> removeList = new ArrayList<Particle>();
		for(Particle p : particles) {
			for (int i = 0; i < s.getNumberOfDimensions(); i++) {
				if(p.pos1[i] > s.getSimulationBoxSize(i) || p.pos1[i] < 0) {
					removeList.add(p);
				}
			}
		}
		particles.removeAll(removeList);
	}

	/**
	 * Interpolates the particle charges to the grid and computes charge conserving currents.
	 *
	 * @param s
	 */
	private void interpolateChargesAndCurrents(Simulation s) {
		double c = as / at;
		// Interpolate particle charges to charge density on the grid
		for(Particle p : particles) {
			// 1) Charge interpolation

			// "Floored" grid points of the particle
			int[] gridPosOld = GridFunctions.flooredGridPoint(p.pos0, as);
			int[] gridPosNew = GridFunctions.flooredGridPoint(p.pos1, as);

			// Cell indices
			int cellIndex0Old = s.grid.getCellIndex(gridPosOld);
			int cellIndex1Old = s.grid.shift(cellIndex0Old, direction, 1);

			int cellIndex0New = s.grid.getCellIndex(gridPosNew);
			int cellIndex1New = s.grid.shift(cellIndex0New, direction, 1);

			// Relative distances to the lattice sites
			double d0New = p.pos1[direction] / as - gridPosNew[direction];
			double d1New = 1 - d0New;
			double d0Old = p.pos0[direction] / as - gridPosOld[direction];
			double d1Old = 1 - d0Old;

			// Links at old and new position
			GroupElement UOld = s.grid.getUnext(cellIndex0Old, direction);
			GroupElement UNew = s.grid.getU(cellIndex0New, direction);

			// Interpolated gauge links
			GroupElement U0New = UNew.getAlgebraElement().mult(d0New).getLink();
			GroupElement U1New = UNew.getAlgebraElement().mult(d1New).getLink().adj();

			// Charge interpolation to neighbouring lattice sites
			AlgebraElement Q0New = p.Q1.act(U0New).mult(d1New);
			AlgebraElement Q1New = p.Q1.act(U1New).mult(d0New);

			s.grid.addRho(cellIndex0New, Q0New);
			s.grid.addRho(cellIndex1New, Q1New);

			// 2) Charge conserving current calculation

			int longitudinalIndexOld = (int) (p.pos0[direction] / as);
			int longitudinalIndexNew = (int) (p.pos1[direction] / as);

			if(longitudinalIndexNew == longitudinalIndexOld) {
				// One-cell move
				GroupElement U0Old = UOld.getAlgebraElement().mult(d0Old).getLink();
				AlgebraElement Q0Old = p.Q0.act(U0Old).mult(d1Old);

				AlgebraElement J = Q0New.sub(Q0Old).mult(-c);
				s.grid.addJ(cellIndex0New, direction, J);

			} else {
				GroupElement U0Old = UOld.getAlgebraElement().mult(d0Old).getLink();
				GroupElement U1Old = UOld.getAlgebraElement().mult(d1Old).getLink().adj();
				AlgebraElement Q0Old = p.Q0.act(U0Old).mult(d1Old);
				AlgebraElement Q1Old = p.Q0.act(U1Old).mult(d0Old);
				if(longitudinalIndexNew > longitudinalIndexOld) {
					// Two-cell move right
					AlgebraElement JOld = Q0Old.mult(c);
					AlgebraElement JNew = JOld.act(s.grid.getU(cellIndex0Old,direction).adj());
					JNew.addAssign(Q0New.sub(Q1Old).mult(-c));

					s.grid.addJ(cellIndex0Old, direction, JOld);
					s.grid.addJ(cellIndex0New, direction, JNew);
				} else {
					// Two-cell move left
					AlgebraElement JNew = Q0New.mult(-c);
					AlgebraElement JOld = JNew.act(UNew.adj());
					JOld.addAssign(Q1New.sub(Q0Old).mult(-c));

					s.grid.addJ(cellIndex0Old, direction, JOld);
					s.grid.addJ(cellIndex0New, direction, JNew);
				}

			}
		}
	}

	/**
	 * Particle class used by the current generator to evolve and interpolate charges on the grid.
	 */
	class Particle {
		public double[] pos0;
		public double[] pos1;
		public double[] vel;

		public AlgebraElement Q0;
		public AlgebraElement Q1;

		public void swap() {
			AlgebraElement tQ = Q0;
			Q0 = Q1;
			Q1 = tQ;

			double[] tPos = pos0;
			pos0 = pos1;
			pos1 = tPos;
		}

		public void move(double dt) {
			for (int i = 0; i < pos0.length; i++) {
				pos1[i] = pos0[i] + vel[i] * dt;
			}
		}

		public void evolve(GroupElement U) {
			Q1 = Q0.act(U.adj());
		}
	}

	/**
	 * Utility class to deal with point charges. Only used to specify the initial conditions.
	 */
	class PointCharge {
		public double[] location;
		public double[] colorDirection;
		double magnitude;

		public PointCharge(double[] location, double[] colorDirection, double magnitude) {
			this.location = location;
			this.colorDirection = normalize(colorDirection);
			this.magnitude = magnitude;
		}

		private double[] normalize(double[] v) {
			double norm = 0.0;
			for (int i = 0; i < v.length; i++) {
				norm += v[i] * v[i];
			}
			norm = Math.sqrt(norm);
			double[] result = new double[v.length];
			for (int i = 0; i < v.length; i++) {
				result[i] = v[i] / norm;
			}
			return result;
		}
	}
}
