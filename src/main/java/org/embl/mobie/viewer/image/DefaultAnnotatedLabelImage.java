package org.embl.mobie.viewer.image;

import net.imglib2.roi.RealMaskRealInterval;
import org.embl.mobie.viewer.annotation.Annotation;
import org.embl.mobie.viewer.annotation.AnnotationAdapter;
import org.embl.mobie.viewer.source.AnnotatedLabelSource;
import org.embl.mobie.viewer.source.AnnotationType;
import org.embl.mobie.viewer.source.SourcePair;
import org.embl.mobie.viewer.source.VolatileAnnotatedLabelSource;
import org.embl.mobie.viewer.table.AnnData;
import net.imglib2.Volatile;
import net.imglib2.type.numeric.IntegerType;

public class DefaultAnnotatedLabelImage< A extends Annotation > implements AnnotatedLabelImage< A >
{
	protected Image< ? extends IntegerType< ? > > labelImage;
	protected AnnData< A > annData;
	protected SourcePair< AnnotationType< A > > sourcePair;
	private RealMaskRealInterval mask;

	public DefaultAnnotatedLabelImage( Image< ? extends IntegerType< ? > > labelImage, AnnData< A > annData )
	{
		this.labelImage = labelImage;
		this.annData = annData;
	}

	@Override
	public SourcePair< AnnotationType< A > > getSourcePair()
	{
		if ( sourcePair == null )
		{
			final SourcePair< ? extends IntegerType< ? > > sourcePair = labelImage.getSourcePair();

			AnnotationAdapter< A > annotationAdapter = new AnnotationAdapter( annData );

			// non-volatile source
			final AnnotatedLabelSource< ?, A > source = new AnnotatedLabelSource( sourcePair.getSource(), annotationAdapter );

			if ( sourcePair.getVolatileSource() == null )
			{
				this.sourcePair = new DefaultSourcePair<>( source, null );
			}
			else
			{
				// volatile source
				final VolatileAnnotatedLabelSource< ?, ? extends Volatile< ? >, A > volatileSource = new VolatileAnnotatedLabelSource( getLabelImage().getSourcePair().getVolatileSource(), annotationAdapter );
				this.sourcePair = new DefaultSourcePair<>( source, volatileSource );
			}
		}

		return sourcePair;
	}

	@Override
	public String getName()
	{
		return labelImage.getName();
	}

	@Override
	public RealMaskRealInterval getMask()
	{
		if ( mask == null )
			return labelImage.getMask();

		return mask;
	}

	@Override
	public void setMask( RealMaskRealInterval mask )
	{
		this.mask = mask;
	}

	@Override
	public AnnData< A > getAnnData()
	{
		return annData;
	}

	@Override
	public Image< ? extends IntegerType< ? > > getLabelImage()
	{
		return labelImage;
	}
}