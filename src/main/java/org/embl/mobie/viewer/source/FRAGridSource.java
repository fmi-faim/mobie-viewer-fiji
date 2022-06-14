/*-
 * #%L
 * Fiji viewer for MoBIE projects
 * %%
 * Copyright (C) 2018 - 2022 EMBL
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.embl.mobie.viewer.source;

import bdv.tools.transformation.TransformedSource;
import bdv.util.Affine3DHelpers;
import bdv.util.DefaultInterpolators;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.roi.RealMaskRealInterval;
import net.imglib2.roi.geom.GeomMasks;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.embl.mobie.viewer.MoBIEHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;

public class FRAGridSource< T extends NumericType< T > > implements Source< T >, RealMaskSource
{
	private final T type;
	private final Source< T > referenceSource;
	private final String mergedGridSourceName;
	private final List< RandomAccessibleInterval< T > > mergedRAIs;
	private final DefaultInterpolators< T > interpolators;
	private final List< Source< T > > gridSources;
	private final List< int[] > positions;
	private final double relativeCellMargin;
	private final boolean encodeSource;
	private int currentTimepoint = 0;
	private Map< String, long[] > sourceNameToVoxelTranslation;
	private int[][] cellDimensions;
	private double[] cellRealDimensions;
	private RealMaskRealInterval mask;
	private int numMipmapLevels;

	public FRAGridSource( List< Source< T > > gridSources, List< int[] > positions, String mergedGridSourceName, double relativeCellMargin, boolean encodeSource )
	{
		this.gridSources = gridSources;
		this.positions = positions;
		this.relativeCellMargin = 0; // relativeCellMargin;
		this.encodeSource = encodeSource;
		this.interpolators = new DefaultInterpolators();
		this.referenceSource = gridSources.get( 0 );
		this.mergedGridSourceName = mergedGridSourceName;
		this.type = referenceSource.getType();

		initDimensions();
		mergedRAIs = createMergedRAIs();
	}

	public static boolean instanceOf( SourceAndConverter< ? > sourceAndConverter )
	{
		final Source< ? > source = sourceAndConverter.getSpimSource();
		return instanceOf( source );
	}

	public static boolean instanceOf( Source< ? > source )
	{
		if ( source instanceof LabelSource )
			source = ( ( LabelSource ) source ).getWrappedSource();

		if ( source instanceof FRAGridSource )
			return true;

		if ( source instanceof TransformedSource )
			source = ( ( TransformedSource ) source ).getWrappedSource();

		if ( source instanceof FRAGridSource )
			return true;

		return false;
	}

	private void initDimensions()
	{
		numMipmapLevels = referenceSource.getNumMipmapLevels();
		setCellDimensions( numMipmapLevels );
		setCellRealDimensions( cellDimensions[ 0 ] );
		setMask( positions, cellDimensions[ 0 ] );
	}

	private List< RandomAccessibleInterval< T > > createMergedRAIs()
	{
		final List< RandomAccessibleInterval< T >> mergedRAIs = new ArrayList<>();
		final Source< T >[][] sourceGrid = createSourceGrid();

		for ( int level = 0; level < numMipmapLevels; level++ )
		{
			final int[] cellDimension = cellDimensions[ level ];
			int finalLevel = level;
			BiConsumer< Localizable, T > biConsumer = ( location, value ) ->
			{
				final int xCellIndex = location.getIntPosition( 0 ) / cellDimension[ 0 ];
				final int yCellIndex = location.getIntPosition( 1 ) / cellDimension[ 1 ];

				final Source< T > source = sourceGrid[ xCellIndex ][ yCellIndex ];
				final int x = location.getIntPosition( 0 ) % xCellIndex;
				final int y = location.getIntPosition( 1 ) % xCellIndex;

				final T v = source.getSource( 0, finalLevel ).randomAccess().setPositionAndGet( x, y, location.getIntPosition( 2 ) );
				value.set( v );
			};

			final FunctionRandomAccessible< T > randomAccessible = new FunctionRandomAccessible( 3, biConsumer, () -> type.createVariable() );

			final long[] dimensions = getDimensions( positions, cellDimension );
			new FinalInterval( dimensions );
			final IntervalView< T > rai = Views.interval( randomAccessible, new FinalInterval( dimensions ) );
			mergedRAIs.add( rai );
		}

		return mergedRAIs;
	}

	private void setCellRealDimensions( int[] cellDimension )
	{
		final AffineTransform3D referenceTransform = new AffineTransform3D();
		referenceSource.getSourceTransform( 0, 0, referenceTransform );
		cellRealDimensions = new double[ 3 ];
		for ( int d = 0; d < 2; d++ )
		{
			cellRealDimensions[ d ] = cellDimension[ d ] * Affine3DHelpers.extractScale( referenceTransform, d );
		}
	}

	public RealMaskRealInterval getRealMask()
	{
		return mask;
	}

	public double[] getCellRealDimensions()
	{
		return cellRealDimensions;
	}

	private void setCellDimensions( int numMipmapLevels )
	{
		final int numDimensions = referenceSource.getVoxelDimensions().numDimensions();

		final AffineTransform3D at3D = new AffineTransform3D();
		referenceSource.getSourceTransform( 0, 0, at3D );

		final double[][] voxelSizes = new double[ numMipmapLevels ][ numDimensions ];
		for ( int level = 0; level < numMipmapLevels; level++ )
		{
			referenceSource.getSourceTransform( 0, level, at3D );
			for ( int d = 0; d < numDimensions; d++ )
				voxelSizes[ level ][ d ] =
						Math.sqrt(
								at3D.get( 0, d ) * at3D.get( 0, d ) +
								at3D.get( 1, d ) * at3D.get( 1, d ) +
								at3D.get( 2, d ) * at3D.get( 2, d )
						);
		}

		double[][] downSamplingFactors = new double[ numMipmapLevels ][ numDimensions ];
		for ( int level = 1; level < numMipmapLevels; level++ )
			for ( int d = 0; d < numDimensions; d++ )
				downSamplingFactors[ level ][ d ] = voxelSizes[ level ][ d ] / voxelSizes[ level - 1 ][ d ];

		final double[] downsamplingFactorProducts = new double[ numDimensions ];
		Arrays.fill( downsamplingFactorProducts, 1.0D );

		for ( int level = 1; level < numMipmapLevels; level++ )
			for ( int d = 0; d < numDimensions; d++ )
				downsamplingFactorProducts[ d ] *= downSamplingFactors[ level ][ d ];

		cellDimensions = new int[ numMipmapLevels ][ numDimensions ];

		// Adapt the cell dimensions such that they are divisible
		// by all relative changes of the resolutions between the different levels.
		// If we don't do this there are jumps of the images when zooming in and out;
		// i.e. the different resolution levels are rendered at slightly offset
		// positions.
		final RandomAccessibleInterval< T > source = referenceSource.getSource( 0, 0 );
		final long[] referenceSourceDimensions = source.dimensionsAsLongArray();
		cellDimensions[ 0 ] = MoBIEHelper.asInts( referenceSourceDimensions );
		for ( int d = 0; d < 2; d++ )
		{
			cellDimensions[ 0 ][ d ] *= ( 1 + 2.0 * relativeCellMargin );
			cellDimensions[ 0 ][ d ] = (int) ( downsamplingFactorProducts[ d ] * Math.ceil( cellDimensions[ 0 ][ d ] / downsamplingFactorProducts[ d ] ) );
		}

		for ( int level = 1; level < numMipmapLevels; level++ )
			for ( int d = 0; d < numDimensions; d++ )
			{
				cellDimensions[ level ][ d ] = (int) ( cellDimensions[ level - 1 ][ d ] / downSamplingFactors[ level ][ d ] );
			}
	}

	private Source[][] createSourceGrid( )
	{
		int maxPos[] = new int[ 2 ];
		for ( int positionIndex = 0; positionIndex < positions.size(); positionIndex++ )
		{
			final int[] position = positions.get( positionIndex );
			for ( int d = 0; d < 2; d++ )
				if ( position[ d ] > maxPos[ d ] )
					maxPos[ d ] = position[ d ];
		}

		final Source[][] sourceGrid = new Source[ maxPos[0]+1 ][ maxPos[1]+1 ];
		for ( int positionIndex = 0; positionIndex < positions.size(); positionIndex++ )
		{
			final int[] position = positions.get( positionIndex );
			sourceGrid[ position[ 0 ] ][ position[ 1 ] ] = gridSources.get( positionIndex );
		}

		return sourceGrid;
	}

	private HashMap< String, long[] > createSourceNameToTranslation( int[] cellDimensions, long[] dataDimensions )
	{
		final HashMap< String, long[] > sourceNameToTranslation = new HashMap<>();

		for ( int positionIndex = 0; positionIndex < positions.size(); positionIndex++ )
		{
			final int[] position = positions.get( positionIndex );
			final long[] cellMin = new long[ 3 ];
			for ( int d = 0; d < 2; d++ )
				cellMin[ d ] = position[ d ] * cellDimensions[ d ];

			final long[] translation = computeTranslation( cellDimensions, cellMin, dataDimensions );

			final Source< T > source = gridSources.get( positionIndex );
			sourceNameToTranslation.put( source.getName(), translation );
		}

		return sourceNameToTranslation;
	}

	private static long[] getDimensions( List< int[] > positions, int[] cellDimensions )
	{
		long[] dimensions = new long[ 3 ];
		final int[] maxPos = new int[ 3 ];
		for ( int d = 0; d < 2; d++ )
		{
			final int finalD = d;
			maxPos[ d ] = positions.stream().mapToInt( pos -> pos[ finalD ] ).max().orElseThrow( NoSuchElementException::new );
		}

		for ( int d = 0; d < 3; d++ )
			dimensions[ d ] = ( maxPos[ d ] + 1 ) * cellDimensions[ d ];

		return dimensions;
	}

	private void setMask( List< int[] > positions, int[] cellDimensions )
	{
		final long[] minPos = new long[ 3 ];
		final long[] maxPos = new long[ 3 ];
		for ( int d = 0; d < 2; d++ )
		{
			final int finalD = d;
			minPos[ d ] = positions.stream().mapToInt( pos -> pos[ finalD ] ).min().orElseThrow( NoSuchElementException::new );
			maxPos[ d ] = positions.stream().mapToInt( pos -> pos[ finalD ] ).max().orElseThrow( NoSuchElementException::new );
		}

		final double[] min = new double[ 3 ];
		final double[] max = new double[ 3 ];

		final AffineTransform3D referenceTransform = new AffineTransform3D();
		referenceSource.getSourceTransform( 0, 0, referenceTransform );

		for ( int d = 0; d < 2; d++ )
		{
			final double scale = Affine3DHelpers.extractScale( referenceTransform, d );
			min[ d ] = minPos[ d ] * cellDimensions[ d ] * scale;
			max[ d ] = ( maxPos[ d ] + 1 ) * cellDimensions[ d ] * scale;
		}

		mask = GeomMasks.closedBox( min, max );
	}


	private static String getCellKey( long[] cellMins )
	{
		String key = "_";
		for ( int d = 0; d < 2; d++ )
			key += cellMins[ d ] + "_";

		return key;
	}

	private long[] computeTranslation( int[] cellDimensions, long[] cellMin, long[] dataDimensions )
	{
		final long[] translation = new long[ cellMin.length ];
		for ( int d = 0; d < 2; d++ )
		{
			// position of the cell + offset for margin
			translation[ d ] = cellMin[ d ] + (long) ( ( cellDimensions[ d ] - dataDimensions[ d ] ) / 2.0 );
		}
		return translation;
	}

	public int[][] getCellDimensions()
	{
		return cellDimensions;
	}

	@Override
	public boolean isPresent( int t )
	{
		return referenceSource.isPresent( t );
	}

	@Override
	public RandomAccessibleInterval< T > getSource( int t, int level )
	{
		if ( t != 0 )
		{
			throw new UnsupportedOperationException( "Multiple time points not yet implemented for merged grid source."); // TODO
		}
		return mergedRAIs.get( level );
	}

	@Override
	public boolean doBoundingBoxCulling()
	{
		return referenceSource.doBoundingBoxCulling();
	}

	@Override
	public RealRandomAccessible< T > getInterpolatedSource( int t, int level, Interpolation method )
	{
		return Views.interpolate( Views.extendZero( getSource( t, level ) ), interpolators.get( method ) );
	}

	@Override
	public void getSourceTransform( int t, int level, AffineTransform3D affineTransform3D )
	{
		referenceSource.getSourceTransform( t, level, affineTransform3D );
	}

	@Override
	public T getType()
	{
		return type;
	}

	@Override
	public String getName()
	{
		return mergedGridSourceName;
	}

	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return referenceSource.getVoxelDimensions();
	}

	@Override
	public int getNumMipmapLevels()
	{
		return referenceSource.getNumMipmapLevels();
	}
}
