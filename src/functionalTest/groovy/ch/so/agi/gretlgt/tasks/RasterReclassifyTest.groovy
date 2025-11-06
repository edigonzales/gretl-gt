package ch.so.agi.gretlgt

import it.geosolutions.jaiext.range.NoDataContainer
import org.geotools.coverage.grid.GridCoverage2D
import org.geotools.coverage.grid.io.AbstractGridFormat
import org.geotools.coverage.grid.io.GridCoverage2DReader
import org.geotools.coverage.grid.io.GridFormatFinder
import org.geotools.coverage.util.CoverageUtilities
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.awt.image.Raster
import java.awt.image.RenderedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.HashSet
import java.util.Set

class RasterReclassifyTest extends Specification {
    private static final double DEFAULT_NO_DATA = -100d

    @TempDir File testProjectDir
    File buildFile

    def setup() {
        buildFile = new File(testProjectDir, "build.gradle")
        buildFile << '''
            plugins { id 'gretl-gt' }
            repositories {
                mavenLocal()
                maven { url "https://jars.sogeo.services/mirror" }
                maven { url "https://repo.osgeo.org/repository/release/" }
                maven { url "https://maven.geo-solutions.it" }
                mavenCentral()
            }
        '''
    }

    def "plugin applies and task is available"() {
        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--all")
            .build()

        then:
        result.output.contains("Gretl")
    }

    def "RasterReclassify task reclassifies raster using defaults"() {
        given:
        Path fixture = Path.of("src/functionalTest/data/RasterReclassify/Beispiel_Rasterfile.asc")
        Path stagedInput = testProjectDir.toPath().resolve("input.asc")
        Files.copy(fixture, stagedInput, StandardCopyOption.REPLACE_EXISTING)

        buildFile << """
            import ch.so.agi.gretlgt.tasks.RasterReclassify

            tasks.register("reclassify", RasterReclassify) {
                inputRaster.set(layout.projectDirectory.file("input.asc"))
                outputRaster.set(layout.buildDirectory.file("reclassified/reclass.tif"))
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("reclassify")
            .forwardOutput()
            .build()

        then:
        result.task(":reclassify").outcome == TaskOutcome.SUCCESS

        File outputFile = new File(testProjectDir, "build/reclassified/reclass.tif")
        outputFile.exists()

        GridCoverage2D coverage = readCoverage(outputFile.toPath())
        Set<Double> values = readClassValues(coverage)
        Set<Double> allowedClasses = [0d, 55d, 60d, 65d, 70d] as Set

        assert values.stream().anyMatch { it != DEFAULT_NO_DATA }
        values.each { value ->
            if (value != DEFAULT_NO_DATA) {
                assert allowedClasses.contains(value)
            }
        }

        NoDataContainer noData = CoverageUtilities.getNoDataProperty(coverage)
        assert noData != null
        assert noData.getAsSingleValue() == DEFAULT_NO_DATA
    }

    def "RasterReclassify task honors explicit breaks configuration"() {
        given:
        Path fixture = Path.of("src/functionalTest/data/RasterReclassify/Beispiel_Rasterfile.asc")
        Path stagedInput = testProjectDir.toPath().resolve("input-breaks.asc")
        Files.copy(fixture, stagedInput, StandardCopyOption.REPLACE_EXISTING)

        buildFile << """
            import ch.so.agi.gretlgt.tasks.RasterReclassify

            tasks.register("reclassifyExplicit", RasterReclassify) {
                inputRaster.set(layout.projectDirectory.file("input-breaks.asc"))
                outputRaster.set(layout.buildDirectory.file("reclassified/custom.tif"))
                breaks.set([0d, 55d, 60d, 65d, 70d, 500d])
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("reclassifyExplicit")
            .forwardOutput()
            .build()

        then:
        result.task(":reclassifyExplicit").outcome == TaskOutcome.SUCCESS

        File outputFile = new File(testProjectDir, "build/reclassified/custom.tif")
        outputFile.exists()

        GridCoverage2D coverage = readCoverage(outputFile.toPath())
        Set<Double> values = readClassValues(coverage)
        Set<Double> expectedClasses = [0d, 55d, 60d, 65d, 70d] as Set

        assert values.stream().anyMatch { it != DEFAULT_NO_DATA }
        values.each { value ->
            if (value != DEFAULT_NO_DATA) {
                assert expectedClasses.contains(value)
            }
        }

        NoDataContainer noData = CoverageUtilities.getNoDataProperty(coverage)
        assert noData != null
        assert noData.getAsSingleValue() == DEFAULT_NO_DATA
    }

    private GridCoverage2D readCoverage(Path rasterPath) {
        AbstractGridFormat format = GridFormatFinder.findFormat(rasterPath.toFile())
        GridCoverage2DReader reader = null
        try {
            reader = format.getReader(rasterPath.toFile())
            return reader.read(null)
        } finally {
            if (reader != null) {
                reader.dispose()
            }
        }
    }

    private Set<Double> readClassValues(GridCoverage2D coverage) {
        RenderedImage image = coverage.getRenderedImage()
        Raster raster = image.getData()

        Set<Double> values = new HashSet<>()
        int width = raster.getWidth()
        int height = raster.getHeight()
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double sample = raster.getSampleDouble(x, y, 0)
                values.add(sample)
            }
        }
        return values
    }
}
