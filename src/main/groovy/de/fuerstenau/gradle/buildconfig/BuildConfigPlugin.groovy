/* The MIT License (MIT)
 *
 * Copyright (c) 2015 Malte Fürstenau
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.fuerstenau.gradle.buildconfig

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.PluginManager
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Malte Fürstenau
 */
class BuildConfigPlugin implements Plugin<Project>
{

    static final String MAIN_SOURCESET = "main"
    static final String FD_SOURCE_OUTPUT = "buildConfigSources"
    static final String FD_CLASS_OUTPUT = "buildConfigClasses"
    static final String DEFAULT_EXTENSION_NAME = "buildConfig"
    static final String DEFAULT_CLASS_NAME = "BuildConfig"
    static final String DEFAULT_SOURCESET = MAIN_SOURCESET
    static final String DEFAULT_NAME_FIELDNAME = "NAME"
    static final String DEFAULT_VERSION_FIELDNAME = "VERSION"
    static final String DEFAULT_PACKAGENAME = "de.fuerstenau.buildconfig"

    private static final Logger LOG = LoggerFactory.getLogger (
        BuildConfigPlugin.class.getCanonicalName ())

    private Project p
    
    /**
     * Return default if value is <i>null</i>.
     *
     * @param <T> Type of value
     * @param mayBeNull Value
     * @param defaultValue Default
     * @return value or <i>null</i> if value was null
     */
    static <T> T defaultIfNull (T mayBeNull, T defaultValue)
    {
        mayBeNull != null ? mayBeNull : defaultValue
    }

    private Configuration getCompileConfiguration (SourceSetConfig cfg)
    {
        String configurationName = MAIN_SOURCESET.equals (cfg.name) ?\
            "compile" : "${cfg.name}Compile"
        try
        {
            p.configurations.getByName (configurationName)
        }
        catch (UnknownConfigurationException ex)
        {
            throw new GradleException (
                "Configuration <${configurationName}> not found. skipping.", ex)
        }
    }

    private SourceSet getSourceSet (SourceSetConfig cfg)
    {
        try
        {
            p.convention.getPlugin (JavaPluginConvention).sourceSets
                .getByName (cfg.name)
        }
        catch (UnknownDomainObjectException ex)
        {
            throw new GradleException (
                    "SourceSet <${cfg.name}> not found. skipping.", ex)
                
        }
    }

    private static String getTaskName (String prefix, String sourceSetName,
        String suffix)
    {
        MAIN_SOURCESET.equals (sourceSetName) ?\
            "${prefix}${suffix}" :
            "${prefix}${sourceSetName.capitalize()}${suffix}"
    }

    @Override
    void apply (Project p)
    {
        this.p = p
        p.apply plugin: 'java'

        /* create the configuration closure */
        p.extensions.create (DEFAULT_EXTENSION_NAME, BuildConfigExtension, p)

        /* evaluate the configuration closure */
        p.afterEvaluate {
            getSourceSetConfigs ().forEach { cfg ->
                Configuration compileCfg = getCompileConfiguration (cfg)
                SourceSet sourceSet = getSourceSet (cfg)

                String generateTaskName = getTaskName ("generate",
                    sourceSet.name, "BuildConfig")
                String compileTaskName = getTaskName ("compile",
                    sourceSet.name, "BuildConfig")

                GenerateBuildConfigTask generate = p.task (generateTaskName, type: GenerateBuildConfigTask) {
                    /* configure generate task with values from the extension */
                    packageName = (cfg.packageName ?: p.group) ?: DEFAULT_PACKAGENAME
                    clsName = cfg.clsName ?: DEFAULT_CLASS_NAME
                    appName = cfg.appName ?: p.name
                    version = cfg.version ?: p.version
                    cfg.buildConfigFields.values().forEach { cf ->
                        addClassField cf
                    }
                }
                
                LOG.debug  "Created task <{}> for sourceSet <{}>.",
                        generateTaskName, cfg.name

                JavaCompile compile =
                        p.task (compileTaskName, type: JavaCompile, dependsOn: generate) {
                    /* configure compile task */
                    classpath = p.files ()
                    destinationDir = new File ("${p.buildDir}/${FD_CLASS_OUTPUT}/${cfg.name}")
                    source = generate.outputDir
                }
                LOG.debug  "Created compiling task <{}> for sourceSet <{}>",
                    compileTaskName,
                    cfg.name

                /* add dependency for sourceset compile configturation */
                compileCfg.dependencies.add (p.dependencies.create (
                        compile.outputs.files))
                
                LOG.debug "Added task <{}> output files as dependency for " +
                    "configuration <{}>", compileTaskName, compileCfg.name
            }
            LOG.debug "BuildConfigPlugin loaded"
        }
    }

    private List<SourceSetConfig> getSourceSetConfigs ()
    {
        new ArrayList<SourceSetConfig> (p.extensions.getByType (BuildConfigExtension)
                    .sourceSets)
    }

    static String getProjectVersion (Project p)
    {
        Object versionObj = p.getVersion ();
        while (versionObj instanceof Closure)
        versionObj = ((Closure) versionObj).call ();
        if (versionObj instanceof String)
        return (String) versionObj;
        return null;
    }

    static String getProjectName (Project p)
    {
        return p.getName ();
    }

    static String getProjectGroup (Project p)
    {
        Object groupObj = p.getGroup ();
        while (groupObj instanceof Closure)
        groupObj = ((Closure) groupObj).call ();
        if (groupObj instanceof String)
        return (String) groupObj;
        return null;
    }
}
