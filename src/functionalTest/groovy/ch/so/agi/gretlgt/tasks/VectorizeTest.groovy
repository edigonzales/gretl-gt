package ch.so.agi.gretlgt

import org.geotools.data.DataStoreFinder
import org.geotools.data.simple.SimpleFeatureIterator
import org.geotools.geopkg.FeatureEntry
import org.geotools.geopkg.GeoPackage
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.locationtech.jts.geom.MultiPolygon
import org.opengis.feature.simple.SimpleFeature
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.HashSet
import java.util.List
import java.util.Set

class VectorizeTest extends Specification {
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

    def "Vectorize task extracts dissolved polygons for requested values"() {
        given:
        Path fixture = Path.of("src/functionalTest/data/Vectorize/reclass.tif")
        Path stagedInput = testProjectDir.toPath().resolve("input.tif")
        Files.copy(fixture, stagedInput, StandardCopyOption.REPLACE_EXISTING)

        buildFile << """
            import ch.so.agi.gretlgt.tasks.Vectorize
        
            System.setProperty("org.geotools.coverage.jaiext.enabled", "true");
        
            tasks.register("vectorize", Vectorize) {
                inputRaster.set(layout.projectDirectory.file("input.tif"))
                outputGeopackage.set(layout.buildDirectory.file("vectorized/output.gpkg"))
                band.set(0)
                cellValues.set([55d, 65d])
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("vectorize", "-s")
            .forwardOutput()
            .build()

        then:
        result.task(":vectorize").outcome == TaskOutcome.SUCCESS

        File outputFile = new File(testProjectDir, "build/vectorized/output.gpkg")
        outputFile.exists()

        Set<Double> extractedValues = new HashSet<>()
        try (GeoPackage geoPackage = new GeoPackage(outputFile)) {
            geoPackage.init()
            List<FeatureEntry> entries = geoPackage.features()
            assert entries.size() == 1
            FeatureEntry entry = entries.get(0)

            def params = [dbtype: 'geopkg', database: outputFile.absolutePath]
            def dataStore = DataStoreFinder.getDataStore(params)
            try {
                def featureSource = dataStore.getFeatureSource(entry.getTableName())
                try (SimpleFeatureIterator iterator = featureSource.getFeatures().features()) {
                    while (iterator.hasNext()) {
                        SimpleFeature feature = iterator.next()
                        assert feature.getDefaultGeometry() instanceof MultiPolygon
                        extractedValues.add(feature.getAttribute("value") as Double)
                    }
                }
            } finally {
                dataStore?.dispose()
            }
        }

        extractedValues.containsAll([55d, 65d])
    }
}