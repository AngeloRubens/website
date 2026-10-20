---
title: "The Maven Build Cache in Practice"
date: "2026-10-20"
description: "The Maven Build Cache significantly reduces build times in enterprise projects. Learn how we introduced it in Azure DevOps pipelines, the issues we encountered, and the valuable lessons we learned along the way."
authors:
  - "mthmulders"
  - "kasper-van-de-wetering"
image: "geocache.jpg"
categories:
  - "Maven"
  - "Use Cases"
related_posts: []
---

The Maven Build Cache can significantly reduce build times, but enabling it is only the beginning. In this
article, we share our experience introducing the cache in Azure DevOps pipelines, the issues we
encountered, and the lessons we learned along the way.

<!--more-->

## Introduction

Apache Maven is by now of legal driving and drinking age in most countries.
A mature and stable build tool for the whole Java ecosystem, Maven is widely used by large enterprises and open-source projects alike.
Still, the community behind Maven continues to improve Maven itself, its plugins and the other
tools around it.
Because Maven is open source, the community has always welcomed fixes and contributions from outside its committers, building a growing community of people that made an effort into making Maven a better product.

## The Maven Build Cache

In 2019, a team at Deutsche Bank [reached out](https://lists.apache.org/thread/z26sm8wvcqq0rcb59z7ngjxlb9swcm7p) to the Apache Maven community, announcing they had implemented an "incremental build" and a "shared cache" - features much like the Gradle build cache.
Using these features, they reduced average build times from 45-60 minutes to 1-2 minutes.
A significant improvement!
And the best part: they wanted to donate these features to the Apache Software Foundation.

How does the Build Cache work?
The idea is rather simple:

1. A CI server runs the build and stores all outputs in a shared cache.
2. The next build on CI can reuse those outputs, provided its inputs haven't changed.
3. A developer who starts working on a feature and builds the project retrieves the snapshots from remote cache for their branch.
4. After changing code, the developer builds their project again. Only the Maven modules with changes get rebuilt.

For this to work, the Maven Build Cache does not look at file timestamps.
Those are not reliable across multiple platforms, machines and filesystems.
Instead, it leverages non-cryptographic-secure but very fast hashes, like the [XX-hash](https://xxhash.com/), to verify whether the contents of the input files have changed.
There are more heuristics to prevent unnecessary rebuilds.
Discussing them is not the main goal of this article; instead, we want to focus on leveraging this for real enterprise projects.

To do this, we're sharing how we did that on a few real-world enterprise projects.
The projects are maintained in Azure DevOps, but we suspect it will work similarly in other environments, such as Gitlab or GitHub.

## The Basics

### Enabling the Build Cache extension

We started by adding the Build Cache extension to the project itself.
This means, adding it to the Maven project by adding an **extensions.xml** file to the **.mvn** folder at the root of our project with the following contents:

```xml
<extensions
    xmlns="http://maven.apache.org/EXTENSIONS/1.1.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.1.0 https://maven.apache.org/xsd/core-extensions-1.1.0.xsd">
  <extension>
    <groupId>org.apache.maven.extensions</groupId>
    <artifactId>maven-build-cache-extension</artifactId>
    <version>1.3.0</version>
  </extension>
</extensions>
```

### Configuring the Build Cache extension

Before discussing the issues we encountered, it's worth covering the minimal setup we started from.
The basic setup is relatively small, and it worked, but the important work started after that.
We observed that some pipeline steps started to fail because output files from an earlier step were missing.
To configure the extension, we added another file to the **.mvn** folder called **maven-build-cache-config.xml**.
Here, we could configure which additional build outputs should be restored.
This was necessary to keep subsequent pipeline steps working correctly.
For the other configuration values, the defaults were good, so we didn't touch them.
All possible configurable options can be found on the [Build Cache extension webpage](https://maven.apache.org/extensions/maven-build-cache-extension/build-cache-config.html).

While evaluating whether the cache was worth adopting, we learned that the extension works especially well for multi-module Maven projects.
It does not check if "the whole project changed" or not.
Instead, it can determine this per Maven module.
If a module and the modules it depends on have not changed, the output of that module can be restored from cache.
It follows that our projects, with more, smaller modules can benefit more.
If a change is isolated to one part of the system, Maven does not necessarily have to rebuild everything.
If most of the code lives in one large module, there is less opportunity to reuse previous work.

For our project, this module-level caching was exactly the interesting part.
Our CI builds spend a lot of time compiling, testing and packaging modules that are often untouched in a pull request.
The build cache gave us a way to avoid repeating that work.

### Setting it up in Azure DevOps pipelines

Adding the extension to the Maven project is only half of the setup.
On a developer machine, the build cache can live in **~/.m2/build-cache**.
In Azure DevOps, however, a build agent starts with a clean workspace.
Without extra pipeline configuration, the cache produced by one pipeline run is not available in the next run.

There are two ways to solve this: one would be to use a remote repository, which you can configure in the configuration file.
The second way, which we will explain in this blog, is to use the [Azure DevOps Cache@2](https://learn.microsoft.com/en-us/azure/devops/pipelines/tasks/reference/cache-v2?view=azure-pipelines) task.
This doesn't allow for reuse by developers but it's a lot easier to set up.
Using a remote repository is a great way if you also want to leverage the cache on a developer machine.
We expected we wouldn't need that.

As mentioned, to persist the caches between Azure DevOps pipeline runs, we use the Cache@2 task in our pipeline template.
The task restores the cache folder at the start of the job and adds a post-job step that saves the cache after the job finishes successfully.
Essentially, we are caching the caches 🤡.

In our case, that looked like this:

```yaml
- task: Cache@2
  displayName: Use Maven build cache
  inputs:
    key: 'maven-build-cache | "$(Agent.OS)" | pom.xml'
    path: $(MAVEN_BUILD_CACHE_FOLDER)
    restoreKeys: |
      maven-build-cache | "$(Agent.OS)"
```

The `$(MAVEN_BUILD_CACHE_FOLDER)` points to the directory used by the Maven Build Cache Extension in our build agent environment.
In our pipeline definition, we define the variable as such: `$(Pipeline.Workspace)/.m2/build-cache`, which is where the cache is normally stored.

Furthermore, we also define a key (and restore key as back-up) to identify the cache.
At first, it can be tempting to make the cache key very specific.
For example, you might include all your project's **pom.xml** files.
For the Maven build cache, that is usually not necessary.
This is because the extension already computes fingerprints based on the inputs of each module.
It "knows" whether a source file changed, whether plugin configuration changed, or whether a module dependency changed.
If we encode too much of that same information into the Azure DevOps cache key, we end up duplicating Maven's logic and creating unnecessary cache misses.

### Using the cache in different pipelines

In our case, the build cache is mainly useful for *pull request validation* and our *dependency check pipeline*.
Pull request builds should give feedback quickly, and they often contain changes to only a small part of the codebase.
This is where module-level caching gives the biggest benefit.
Running the dependency checker shouldn't require a full build; cached artifacts can be used just fine here.

We deliberately did not rely on the Maven build cache in the *deployment pipeline*.
For deployment builds, we want the produced artifact to come from a clean build.
The deployment pipeline is not the place where we wanted to optimise aggressively by restoring previous build outputs.

Summarising, our setup consists of three parts:

1. Pull request pipeline: use the Maven build cache to speed up validation. 
2. Dependency check pipeline: there is no need to rebuild the entire project just to run the dependency check plugin.
3. Deployment pipeline: build without relying on restored build outputs.

It's important to note that Azure DevOps caches are scoped to the branch that is checked out in the pipeline.
This means each pull request for a different branch requires an initial full build before being able to reuse caches.
Later, we might consider the main deployment pipeline to create caches but not consume them; this would improve even the first build of each pull request.
Since pull request builds can only **read** caches from the target branch, we could then "prime" a cache by building the main branch.
After that, pull requests targeting the main branch can reuse that cache.

### Specific issues we encountered

When integrating the extension into our project, we inevitably encountered some problems.

#### Restoring only the artifact(s) was not enough

One of the first issues we encountered was that restoring the final artifact alone was not enough for our pipeline.

By default, the build cache extension does not restore the entire target directory.
This matters, because other tools or plugins in the pipeline or build often do not look only at the final **.jar**, **.war**, or **.ear** file.
They also expect files in the module's target folder.

For example, JaCoCo and reporting tools may look for files in directories such as:

* **target/classes**
* **target/test-classes**
* **target/surefire-reports**
* **target/site/jacoco**

When Maven restores a module from cache, but these directories are missing, later pipeline steps can fail or produce incomplete reports.
The module itself may technically be "built" from Maven's perspective, but the rest of the pipeline still expects the usual build output layout.

To fix this, we configured the build cache extension to attach additional output directories.
In our case, the relevant directories were:

```xml
<attachedOutputs>
  <dirNames>
    <dirName>classes</dirName>
    <dirName>test-classes</dirName>
    <dirName>surefire-reports</dirName>
    <dirName>site/jacoco</dirName>
  </dirNames>
</attachedOutputs>
```

This tells the extension to store and restore these directories as part of the cached output.
After this change, a restored module once again had the files needed by the next steps in the pipeline.

#### SonarCloud analysis and the Maven reactor

The next issue appeared around SonarCloud analysis.

Before introducing the build cache, our pipeline used the Sonar integration on the [Maven task](https://learn.microsoft.com/en-us/azure/devops/pipelines/tasks/reference/maven-v4?view=azure-pipelines) itself.
That meant the build, tests, packaging, and Sonar analysis were all part of one Maven invocation.
After enabling caching, that setup became problematic.

It took us a while to understand what was happening.
The root cause is that the `sonar:sonar` goal is not a normal lifecycle phase like `compile`, `test`, `package`, or `install`.
It is a standalone Maven goal.
We observed that the Maven Build Cache Extension tries to cache the Sonar goal just like the normal lifecycle goals.
But when restoring the cache, the extension did not know how to handle the Sonar goal, resulting in errors.

The solution was to separate Sonar analysis into its own Maven task:

```yaml
- task: Maven@4
  displayName: Build and test
  inputs:
    mavenPomFile: 'pom.xml'
    goals: 'package'
    mavenOptions: '$(MAVEN_OPTS)'

- task: Maven@4
  displayName: Run SonarCloud analysis
  inputs:
    mavenPomFile: 'pom.xml'
    goals: 'sonar:sonar'
    mavenOptions: '$(MAVEN_OPTS)'
```

Splitting the step solved one problem but introduced another.

In the original setup, Sonar ran inside the same Maven reactor as the build.
Maven could resolve dependencies between modules directly from the reactor.
After splitting Sonar into a second Maven invocation, that reactor context was gone.
The second Maven process had to resolve inter-module dependencies from the local Maven repository.

That is where we saw errors like:

```text
The following dependencies could not be resolved at this point of the build but seem to be part of the reactor.
```

Indicating that our own internal modules couldn't be resolved.

The root cause was that the first Maven step used to run `package`.
The `package` goal creates the module artifacts, but it does not install them into the local Maven repository.
So, when the second Maven invocation started, the artifacts existed in target, but not in the local repository where Maven was looking for them.

Caching **target** was not enough here.
A separate Maven invocation needs the artifacts either:

* in the same reactor, or
* installed into the local Maven repository.

Because Sonar now ran in a separate Maven process, we changed the build step to run `install` instead of only `package`.

That way, even if a module is restored from cache, its artifact is also made available in the local Maven repository for later Maven invocations inside the same pipeline.

To make this reliable with the build cache, we configured the Maven install goal to always run by adding the following XML to the **maven-build-cache-config.xml** file:

```xml
<!-- namespace declarations omitted for brevity -->
<cache>
  <configuration>
    <!-- other config omitted for brevity -->
    <executionControl>
      <runAlways>
        <goalsLists>
          <goalsList groupId="org.apache.maven.plugins"
                    artifactId="maven-install-plugin">
            <goals>
              <goal>install</goal>
            </goals>
          </goalsList>
        </goalsLists>
      </runAlways>
    </executionControl>
  </configuration>
</cache>
```

The build cache can still restore the expensive parts of the build, such as compilation, tests, and packaging.
But the `install` step is cheap enough to run every time, and it ensures that later Maven invocations can resolve the module artifacts.

#### OWASP Dependency Check

We ran into a similar issue with the [OWASP Dependency Check plug-in](https://dependency-check.github.io/DependencyCheck/index.html), which we only ran in a dedicated pipeline.
Originally, Dependency Check was integrated into the main Maven execution.
In our project, it was bound to the `verify` phase and used the `aggregate` goal, which is useful for multi-module projects because it produces a combined report.

However, it also did not play nicely with the build cache.
Dependency Check does not produce the kind of reusable build output that we want to restore from cache.
It depends on the current dependency graph and vulnerability data, so it is better treated as a step that should run explicitly when needed.

A simplified setup looks like this:

```yaml
- task: Maven@4
  displayName: Build and install modules
  inputs:
    mavenPomFile: 'pom.xml'
    goals: 'install'
    mavenOptions: $(MAVEN_OPTS)

- task: Maven@4
  displayName: Run OWASP Dependency Check
  inputs:
    mavenPomFile: 'pom.xml'
    goals: 'org.owasp:dependency-check-maven:aggregate'
    mavenOptions: '$(MAVEN_OPTS)'
```

## Results

Now that we have installed and configured the extension, let's reflect on why we set out to experiment with it.
Did using the cache extension really help us? What value did it bring?

To be clear, our main goal was not to make every single build magically fast.
The goal was more specific: reduce the feedback time for pull request builds.
Those builds are run frequently, they are on the critical path for code reviews, and in many cases they validate changes that only touch a small part of the codebase.
That makes them a good candidate for build caching.

Before enabling the Build Cache Extension, a full pull request pipeline build would take about 18 minutes, of which 14 were spent compiling, running tests and packaging the application.
The other four minutes were spent running minor pre- and post-job steps, as well as the SonarCloud analysis.
Whenever new changes were pushed to Azure DevOps, no matter how large or small, that entire 18-minute build had to be run again.

After enabling the build cache, the first build for a branch still behaves mostly like a full build.
This is expected: there is nothing useful to restore yet, the benefits appear in later builds.
However, once that initial cache was created, the improvements were clear.
In the best case, the Maven `install` step only took a minute!
On average, depending on the scope of the changes and how many other modules are dependent on said changes, the Maven `install` step takes about five to six minutes.
That is a 50% reduction in the overall pipeline build duration!

We also observed that these shorter builds gave us another benefit, more of an operational impact.
We got feedback on proposed changes much earlier in the process.
Knowing we had to wait only 9 minutes instead of 18 minutes, we felt less tempted to switch to another task.
We knew we could quickly fix an issue and continue the review process.

In practice, that contributed to delivery speed in a few ways:

* **Shorter feedback loops.**
  Build failures, test failures and static analysis issues are reported sooner.
  This makes it easier to fix small issues while the change is still fresh in the developer's mind.
* **Less waiting during code review.**
  Reviewers do not have to wait as long before a pull request is green again after changes are pushed.
  This helps keep reviews moving.
* **More confidence to make smaller changes.**
  If every build of a pull request takes a long time, there is always a temptation to batch multiple changes together.
  Faster validation makes it easier to keep pull requests smaller and more focused.
* **Better use of CI capacity.**
  The build agents still do work, but they spend less time repeating expensive steps for modules that have already been built before.
  This is especially useful when several pull requests are active at the same time.

As described earlier, we initially expected that restoring the final artifact would be enough.
In reality, later steps also depended on files in the target directory, such as compiled classes, test classes, Surefire reports and JaCoCo output.
Without restoring those outputs, Maven could consider a module restored, while reporting or analysis steps still failed or produced incomplete results.

## Lessons learned

The main thing we learned is that build caching is not only a technical optimisation.
It changes how you look at the build as part of the development process.

Before introducing the cache, we mostly treated the build as one large step: Maven starts, Maven builds everything, and after some time the pipeline either passes or fails.
The build cache forced us to look more carefully at what artifacts each part of the build produces, which parts are needed by later steps, and which steps are safe to restore from cache.

That was useful beyond the cache itself.
It made the pipeline more explicit.

For example, splitting SonarCloud analysis and Dependency Check into separate Maven invocations made the pipeline easier to reason about.
It also made the responsibilities clearer:

* the build step compiles, tests, packages and installs the modules;
* the SonarCloud step performs code analysis;
* the Dependency Check step performs dependency analysis;
* the deployment pipeline produces the artifacts we deploy.

That separation helped us avoid treating every pipeline as the same kind of build.
A pull request pipeline has different goals than a deployment pipeline.
A pull request build should provide fast feedback.
A deployment build should be clean, reproducible and trustworthy.
Those are related goals, but they are not identical.

From an organisational perspective, the biggest benefit is the shorter feedback loop.
When developers get feedback faster, the team loses less time waiting for CI.
It becomes easier to address review comments quickly, keep pull requests moving, and avoid context switching.
That does not automatically make a team faster, but it removes one of the small frictions that slows teams down every day.

## Conclusion

It's time to wrap up.
If this made you enthusiastic and you want to get started, these are the main points to be aware of:

1. **Measure your current build times.**
   Do not start with the configuration, start with a baseline.
   Look at a few recent builds on your main branch and a few recent pull request builds.
   Try to understand [where the time is spent](https://maarten.mulders.it/2024/03/measure-your-maven-build/): compilation, tests, packaging, analysis tools, dependency checks, or something else.
   Without that baseline, it is hard to know whether the cache will help.
2. **Check which files your later pipeline steps expect.**
   Do not assume that restoring the final artifact is enough.
   If you use JaCoCo, Surefire reports, SonarCloud, Dependency Check or other tools that inspect the build output, make sure their required files are either regenerated or restored from cache.
3. **Be careful with separate Maven invocations.**
   If one Maven step builds the project and another Maven step analyses it, remember that the second step no longer has the reactor context from the first step.
   If it needs your own modules, those modules should be available in the local Maven repository.
   In our case, that meant running install and configuring the install goal to always run.
4. **Treat the first version as something you will tune.**
   The extension is powerful, but every project has its own build structure, plugins and reporting steps.
   We did not get everything right on the first attempt.
   The useful approach was to enable it, inspect what was restored, fix the missing outputs, and explicitly mark the goals that should always run.

As we look back, we see that the Build Cache was not a switch we could easily ‘turn on'.
It was something we had to introduce gradually while learning how our pipelines behave.
Doing so gave us better insight in our pipelines, a better separation of concerns.
Most importantly, we gained confidence we're doing the right thing: both in terms of how we build (the pipelines, the tests, etc.) and in what we build (the product itself).

## References & Further Reading

* [Maven Build Cache Extension](https://maven.apache.org/extensions/maven-build-cache-extension/)
* [xxHash](https://xxhash.com/)
* [Build Cache configuration reference](https://maven.apache.org/extensions/maven-build-cache-extension/build-cache-config.html)
* [Initial announcement of Deutsche Bank's intention to contribute the Build Cache](https://lists.apache.org/thread/z26sm8wvcqq0rcb59z7ngjxlb9swcm7p)
* [Initial merge request from Deutsche Bank](https://github.com/apache/maven/pull/526)
* [Measure Your Maven Build](https://maarten.mulders.it/2024/03/measure-your-maven-build/)
* [The Road to Reproducible Builds on the JVM](https://maarten.mulders.it/2026/09/the-road-to-reproducible-builds-on-the-jvm/)
