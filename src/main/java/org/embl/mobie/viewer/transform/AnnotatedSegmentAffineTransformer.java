package org.embl.mobie.viewer.transform;

import net.imglib2.realtransform.AffineTransform3D;
import org.embl.mobie.viewer.annotation.AnnotatedSegment;

public class AnnotatedSegmentAffineTransformer implements AnnotationTransformer< AnnotatedSegment, AffineTransformedAnnotatedSegment >
{
	private AffineTransform3D transformation;

	public AnnotatedSegmentAffineTransformer( AffineTransform3D affineTransform3D )
	{
		this.transformation = affineTransform3D;
	}

	@Override
	public AffineTransformedAnnotatedSegment transform( AnnotatedSegment annotatedSegment )
	{
		return new AffineTransformedAnnotatedSegment( annotatedSegment, transformation );
	}
}