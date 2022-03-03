package org.embl.mobie.viewer.playground;

import bdv.util.Affine3DHelpers;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SynchronizedViewerState;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;
import org.embl.mobie.viewer.transform.TransformHelpers;

import java.util.List;
import java.util.stream.Collectors;

public class MoBIEViewerTransformAdjuster implements Runnable {
	private final BdvHandle bdvHandle;
	private final List< SourceAndConverter< ? > > sources;

	public MoBIEViewerTransformAdjuster( BdvHandle bdvHandle, List< SourceAndConverter< ? > > sources) {
		this.bdvHandle = bdvHandle;
		this.sources = sources;
	}

	public void run()
	{
		if ( this.sources.size() != 0 )
		{
			AffineTransform3D transform = getTransform();
			transform = getTransformMultiSources();
			bdvHandle.getViewerPanel().state().setViewerTransform( transform );
		}
	}

	public AffineTransform3D getTransform() {
		SynchronizedViewerState state = this.bdvHandle.getViewerPanel().state();
		int viewerWidth = this.bdvHandle.getBdvHandle().getViewerPanel().getWidth();
		int viewerHeight = this.bdvHandle.getBdvHandle().getViewerPanel().getHeight();
		double cX = (double)viewerWidth / 2.0D;
		double cY = (double)viewerHeight / 2.0D;
		int timepoint = state.getCurrentTimepoint();
		SourceAndConverter source = this.sources.get( 0 );
		if (! source.getSpimSource().isPresent(timepoint)) {
			return new AffineTransform3D();
		} else {
			AffineTransform3D sourceTransform = new AffineTransform3D();
			source.getSpimSource().getSourceTransform(timepoint, 0, sourceTransform);
			Interval sourceInterval = source.getSpimSource().getSource(timepoint, 0);
			double sX0 = (double)sourceInterval.min(0);
			double sX1 = (double)sourceInterval.max(0);
			double sY0 = (double)sourceInterval.min(1);
			double sY1 = (double)sourceInterval.max(1);
			double sZ0 = (double)sourceInterval.min(2);
			double sZ1 = (double)sourceInterval.max(2);
			double sX = (sX0 + sX1) / 2.0D;
			double sY = (sY0 + sY1) / 2.0D;
			double sZ = (double)Math.round((sZ0 + sZ1) / 2.0D);
			double[][] m = new double[3][4];
			double[] qSource = new double[4];
			double[] qViewer = new double[4];

			Affine3DHelpers.extractApproximateRotationAffine(sourceTransform, qSource, 2);
			LinAlgHelpers.quaternionInvert(qSource, qViewer);
			LinAlgHelpers.quaternionToR(qViewer, m);

			double[] centerSource = new double[]{sX, sY, sZ};
			double[] centerGlobal = new double[3];
			double[] translation = new double[3];
			sourceTransform.apply(centerSource, centerGlobal);
			double[] pSource = new double[]{sX1 + 0.5D, sY1 + 0.5D, sZ};
			double[] pGlobal = new double[3];
			double[] pScreen = new double[3];
			sourceTransform.apply(pSource, pGlobal);


			LinAlgHelpers.quaternionApply(qViewer, centerGlobal, translation);
			LinAlgHelpers.scale(translation, -1.0D, translation);
			LinAlgHelpers.setCol(3, translation, m);
			AffineTransform3D viewerTransform = new AffineTransform3D();
			viewerTransform.set(m);
			viewerTransform.apply(pGlobal, pScreen);

			double scaleX = cX / pScreen[0];
			double scaleY = cY / pScreen[1];
			double scale = Math.min(scaleX, scaleY);
			viewerTransform.scale(scale);
			viewerTransform.set(viewerTransform.get(0, 3) + cX - 0.5D, 0, 3);
			viewerTransform.set(viewerTransform.get(1, 3) + cY - 0.5D, 1, 3);
			return viewerTransform;
		}
	}

	public AffineTransform3D getTransformMultiSources() {
		SynchronizedViewerState state = this.bdvHandle.getViewerPanel().state();
		final RealInterval bounds = TransformHelpers.estimateBounds( sources.stream().map( sac -> sac.getSpimSource() ).collect( Collectors.toList() ), state.getCurrentTimepoint() );
		return TransformHelpers.getIntervalViewerTransorm( bdvHandle, bounds );
	}

}

