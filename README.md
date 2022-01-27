# OLM Bundle Plugin for Java projects

This is custom maven plugin that can be used to generate a OLM bundle image from your Quarkus project. It is expected that the dependencies you define to the build are yaml files and it reads all the yaml files defined as dependencies and looks for resources that are required for building a CSV, CRD files.

See the `example` for an example configuration 