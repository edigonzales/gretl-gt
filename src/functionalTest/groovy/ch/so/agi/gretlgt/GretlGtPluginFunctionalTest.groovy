package ch.so.agi.gretlgt

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class GretlGtPluginFunctionalTest extends Specification {

    @TempDir File testProjectDir
    File buildFile

    def setup() {
        buildFile = new File(testProjectDir, "build.gradle")
        buildFile << '''
            plugins { id 'gretl-gt' }
            repositories {
                mavenCentral()
                maven { url = 'https://repo.osgeo.org/repository/release/' }
            }
        '''
    }

    def "plugin applies and task is available"() {
        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("tasks")
            .build()

        then:
        result.output.contains("Gretl")
    }
}
