package org.openpixi.pixi.physics.grid;

import java.util.ArrayList;
import org.openpixi.pixi.physics.*;

public class YeeGrid extends Grid {
	
	public YeeGrid(Simulation s) {
		
		super(s);

		numCellsX = 10;
		numCellsY = 10;
		cellWidth = s.width/numCellsX;
		cellHeight = s.height/numCellsY;

		interp = new ChargeConservingAreaWeighting(this);

		jx = new double[numCellsX][numCellsY];
		jy = new double[numCellsX][numCellsY];
		rho = new double[numCellsX][numCellsY];
		Ex = new double[numCellsX][numCellsY];
		Ey = new double[numCellsX][numCellsY];
		Bz = new double[numCellsX][numCellsY];
		initFields();
		
	}
	
	//a method to change the dimensions of the cells, i.e. the width and the height
	public void changeDimension(double width, double height, int xbox, int ybox)
	{
		numCellsX = xbox;
		numCellsY = ybox;
		
		jx = new double[numCellsX][numCellsY];
		jy = new double[numCellsX][numCellsY];
		rho = new double[numCellsX][numCellsY];
		Ex = new double[numCellsX][numCellsY];
		Ey = new double[numCellsX][numCellsY];
		Bz = new double[numCellsX][numCellsY];
		initFields();
		
		setGrid(width, height);
	}
	
	public void setGrid(double width, double height)
	{
		cellWidth = width / numCellsX;
		cellHeight = height / numCellsY;
		
		for (Particle2D p: s.particles){
			//assuming rectangular particle shape i.e. area weighting
			p.pd.cd = p.charge / (cellWidth * cellHeight);
		}
		
		//include updateGrid() and the first calculation of Fields here
	}
	
	public void updateGrid(ArrayList<Particle2D> particles) {
		
		reset();
		interp.interpolateToGrid(particles);
		s.fsolver.step(this);
		interp.interpolateToParticle(particles);
	}
	
	private void reset() {
		for(int i = 0; i < numCellsX; i++) {
			for(int k = 0; k < numCellsY; k++) {
				jx[i][k] = 0.0;
				jy[i][k] = 0.0;
				rho[i][k] = 0.0;
			}
		}
	}
	
	private void initFields() {
		for (int i = 0; i < numCellsX; i++) {
			for (int j = 0; j < numCellsY; j++) {
				Ex[i][j] = 0.0;
				Ey[i][j] = 0.0;
				Bz[i][j] = 0.0;
			}
		}
	}

}
