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
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.roi.RealMaskRealInterval;
import net.imglib2.roi.geom.GeomMasks;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.view.Views;
import org.embl.mobie.viewer.MoBIEHelper;
import org.embl.mobie.viewer.SourceNameEncoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

public class MergedGridSource< T extends NativeType< T > & NumericType< T > > implements Source< T >, RealMaskSource
{
	private final T type;
	private final Source< T > referenceSource;
	private final String mergedGridSourceName;
	private final List< RandomAccessibleInterval< T > > mergedRandomAccessibleIntervals;
	private final DefaultInterpolators< T > interpolators;
	private final List< SourceAndConverter< T > > gridSources;
	private final List< int[] > positions;
	private final double relativeCellMargin;
	private final boolean encodeSource;
	private int currentTimepoint = 0;
	private Map< String, long[] > sourceNameToVoxelTranslation;
	private int[][] cellDimensions;
	private double[] cellRealDimensions;
	private Set< SourceAndConverter > containedSourceAndConverters;
	private RealMaskRealInterval mask;

	public MergedGridSource( List< SourceAndConverter< T > > gridSources, List< int[] > positions, String mergedGridSourceName, double relativeCellMargin, boolean encodeSource )
	{
		this.gridSources = gridSources;
		this.positions = positions;
		this.relativeCellMargin = relativeCellMargin;
		this.encodeSource = encodeSource;
		this.interpolators = new DefaultInterpolators<>();
		this.referenceSource = gridSources.get( 0 ).getSpimSource();
		this.mergedGridSourceName = mergedGridSourceName;
		this.type = referenceSource.getType();

		initDimensions();
		mergedRandomAccessibleIntervals = createMergedRAIs( referenceSource.getNumMipmapLevels() );
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

		if ( source instanceof MergedGridSource )
			return true;

		if ( source instanceof TransformedSource )
			source = ( ( TransformedSource ) source ).getWrappedSource();

		if ( source instanceof MergedGridSource )
			return true;

		return false;
	}

	public List< SourceAndConverter< T > > getGridSources()
	{
		return gridSources;
	}

	private void initDimensions()
	{
		final int numMipmapLevels = referenceSource.getNumMipmapLevels();
		setCellDimensions( numMipmapLevels );
		setCellRealDimensions( cellDimensions[ 0 ] );
		setMask( positions, cellDimensions[ 0 ] );
	}

	private List< RandomAccessibleInterval< T > > createMergedRAIs( int numMipmapLevels )
	{
		final List< RandomAccessibleInterval< T >> mergedRandomAccessibleIntervals = new ArrayList<>();

		for ( int level = 0; level < numMipmapLevels; level++ )
		{
			long[] mergedDimensions = getDimensions( positions, cellDimensions[ level ] );

			final Map< String, SourceAndConverter< T > > cellKeyToSource = createCellKeyToSource( cellDimensions[ level ] );

			if ( level == 0 )
			{
				sourceNameToVoxelTranslation = createSourceNameToTranslation( cellDimensions[ level ], gridSources.get( 0 ).getSpimSource().getSource( 0, 0 ).dimensionsAsLongArray() );
			}

			final RandomAccessibleIntervalCellLoader< T > cellLoader = new RandomAccessibleIntervalCellLoader( cellKeyToSource, level );

			final CachedCellImg< T, ? > cachedCellImg =
					new ReadOnlyCachedCellImgFactory().create(
						mergedDimensions,
						type,
						cellLoader,
						ReadOnlyCachedCellImgOptions.options().cellDimensions( cellDimensions[ level ] ) );


			mergedRandomAccessibleIntervals.add( cachedCellImg );
		}

		return mergedRandomAccessibleIntervals;
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

	private Map< String, SourceAndConverter< T > > createCellKeyToSource( int[] cellDimensions )
	{
		final Map< String, SourceAndConverter< T > > cellKeyToSource = new HashMap<>();
		for ( int positionIndex = 0; positionIndex < positions.size(); positionIndex++ )
		{
			final int[] position = positions.get( positionIndex );
			final long[] cellMins = new long[ 3 ];
			for ( int d = 0; d < 2; d++ )
				cellMins[ d ] = position[ d ] * cellDimensions[ d ];

			String key = getCellKey( cellMins );
			cellKeyToSource.put( key, gridSources.get( positionIndex ) );

		}
		return cellKeyToSource;
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

			final SourceAndConverter< T > sourceAndConverter = gridSources.get( positionIndex );
			sourceNameToTranslation.put( sourceAndConverter.getSpimSource().getName(), translation );
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
		return mergedRandomAccessibleIntervals.get( level );
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

	public void setContainedSourceAndConverters( Set< SourceAndConverter > containedSourceAndConverters )
	{
		this.containedSourceAndConverters = containedSourceAndConverters;
	}

	class RandomAccessibleIntervalCellLoader< T extends NativeType< T > > implements CellLoader< T >
	{
		private final Map< String, SourceAndConverter< T > > cellKeyToSource;
		private final int level;

		public RandomAccessibleIntervalCellLoader( Map< String, SourceAndConverter< T > > cellKeyToSource, int level )
		{
			this.cellKeyToSource = cellKeyToSource;
			this.level = level;
		}

		@Override
		public void load( SingleCellArrayImg< T, ? > cell ) throws Exception
		{
			final String cellKey = getCellKey( cell.minAsLongArray() );

			if ( ! cellKeyToSource.containsKey( cellKey ) )
			{
				return;
			}
			else
			{
				// Get the RAI for this cell
				final Source< T > source = cellKeyToSource.get( cellKey ).getSpimSource();
				RandomAccessibleInterval< T > data = source.getSource( currentTimepoint, level );

				// Create a view that is shifted to the cell position
				final long[] offset = computeTranslation( MoBIEHelper.asInts( cell.dimensionsAsLongArray() ), cell.minAsLongArray(), data.dimensionsAsLongArray() );
				data = Views.translate( Views.zeroMin( data ), offset );

				// copy RAI into cell
				Cursor< T > sourceCursor = Views.iterable( data ).cursor();
				RandomAccess< T > targetAccess = cell.randomAccess();

				if ( encodeSource )
				{
					final String name = source.getName();
					while ( sourceCursor.hasNext() )
					{
						sourceCursor.fwd();
						// copy the sourceCursor in order not to modify it by the source name encoding
						targetAccess.setPositionAndGet( sourceCursor ).set( sourceCursor.get().copy() );
						SourceNameEncoder.encodeName( ( UnsignedIntType ) targetAccess.get(), name );
					}
				}
				else
				{
					while ( sourceCursor.hasNext() )
					{
						sourceCursor.fwd();
						targetAccess.setPositionAndGet( sourceCursor ).set( sourceCursor.get() );
					}
				}
			}
		}
	}
}
