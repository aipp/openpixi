package org.openpixi.pixi.physics.fields;

import org.openpixi.pixi.physics.grid.Grid;

public class SimpleSolver extends FieldSolver {

	double cx, cy, cz;

	public SimpleSolver() {

	}

	/**A simple LeapFrog algorithm
	 * @param p before the update: E(t), B(t+dt/2);
	 * 						after the update: E(t+dt), B(t+3dt/2)
	*/
	@Override
	public void step(Grid g) {

		for (int i = 1; i < g.getNumCellsX() - 1; i++) {
			for (int j = 1; j < g.getNumCellsY() - 1; j++) {

				/**curl of the E field using center difference*/
				cz = (g.getEyo(i+1, j) - g.getEyo(i-1,j)) / ( 2 * g.getCellWidth()) -
						(g.getExo(i, j+1) - g.getExo(i, j-1)) / ( 2 * g.getCellHeight());

				/**Maxwell equations*/
				g.addBz(i,j, -g.simulation.tstep * cz);

				/**curl of the B field using center difference*/
				cx = (g.getBzo(i,j+1) - g.getBzo(i,j-1)) / ( 2 * g.getCellHeight());
				cy = -(g.getBzo(i+1,j) - g.getBzo(i-1,j)) / ( 2 * g.getCellWidth());

				/**Maxwell EQ*/
				g.addEx(i,j, g.simulation.tstep * (cx - g.getJx(i,j)));
				g.addEy(i,j, g.simulation.tstep * (cy - g.getJy(i,j)));
			}
		}
	}

}
