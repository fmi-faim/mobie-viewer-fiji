package projects;

import de.embl.cba.mobie.ui.MoBIE;
import net.imagej.ImageJ;

public class OpenRemoteArabidopsis
{
	public static void main( String[] args )
	{
		final ImageJ imageJ = new ImageJ();
		imageJ.ui().showUI();

		new MoBIE("https://github.com/mobie/arabidopsis-root-lm-datasets" );
	}
}