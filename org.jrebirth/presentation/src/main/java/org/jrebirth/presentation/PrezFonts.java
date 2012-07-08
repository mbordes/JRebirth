package org.jrebirth.presentation;

import javafx.scene.text.Font;

import org.jrebirth.core.resource.ResourceBuilders;
import org.jrebirth.core.resource.font.FontBuilder;
import org.jrebirth.core.resource.font.FontEnum;
import org.jrebirth.core.resource.font.FontParams;
import org.jrebirth.core.resource.font.RealFont;

/**
 * The class <strong>PrezFonts</strong>.
 * 
 * @author Sébastien Bordes
 * 
 */
public enum PrezFonts implements FontEnum {

    /** The splash font. */
    PAGE(new RealFont(FontsLoader.BorisBlackBloxx, 36)),

    /** The slide title font. */
    SLIDE_TITLE(new RealFont(FontsLoader.Harabara, 45)),

    /** The slide title font. */
    SLIDE_SUB_TITLE(new RealFont(FontsLoader.Harabara, 30)),

    /** The typewriter font. */
    TYPEWRITER(new RealFont(FontsLoader.Report_1942, 72)),

    /** The splash font. */
    SPLASH(new RealFont(FontsLoader.BorisBlackBloxx, 30));

    /**
     * Default Constructor.
     * 
     * @param fontParams the font size
     */
    PrezFonts(final FontParams fontParams) {
        factory().storeParams(this, fontParams);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Font get() {
        return factory().get(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FontBuilder factory() {
        return (FontBuilder) ResourceBuilders.FONT_BUILDER.use();
    }

}
