# massql-cytoscape-app — the ONLY entry point for building and testing. Do not invoke `gradlew`
# directly; add a target here instead.

GRADLE := ./gradlew --console=plain

.DEFAULT_GOAL := help
.PHONY: help all build test integration-test lint lint-fix coverage clean install install-app

## help: list the targets (default)
help:
	@echo "massql-cytoscape-app — make targets"
	@echo
	@grep -E '^## [a-z-]+:' $(MAKEFILE_LIST) | sed 's/^## /  /' | sort
	@echo
	@echo "  Never run ./gradlew directly — add a target instead."

## all: alias for integration-test
all: integration-test

## build: compile and package the OSGi bundle jar
build:
	$(GRADLE) jar
	@ls -1 build/libs/*.jar | sed 's|^|  -> |'

## test: unit tier only. Seconds, for the edit loop.
test:
	$(GRADLE) test

## integration-test: everything -- both tiers, the assembled-bundle smoke test, and lint
integration-test:
	$(GRADLE) check

## lint: report style violations (Spotless is the whole style specification)
lint:
	$(GRADLE) spotlessCheck

## lint-fix: fix them
lint-fix:
	$(GRADLE) spotlessApply

## coverage: write the JaCoCo report
coverage:
	$(GRADLE) jacocoTestReport
	@echo "  -> build/reports/jacoco/test/html/index.html"

## clean: remove build output
clean:
	$(GRADLE) clean

## install: install the jar into the local Maven repository
install:
	$(GRADLE) publishToMavenLocal

## install-app: drop the bundle into a running Cytoscape, which hot-installs it
install-app: build
	@mkdir -p "$(HOME)/CytoscapeConfiguration/3/apps/installed"
	@cp build/libs/*.jar "$(HOME)/CytoscapeConfiguration/3/apps/installed/"
	@ls -1 build/libs/*.jar | xargs -n1 basename \
	  | sed 's|^|  -> $(HOME)/CytoscapeConfiguration/3/apps/installed/|'
