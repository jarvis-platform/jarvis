/*
 * generated by Xtext 2.12.0
 */
package edu.uoc.som.jarvis.language.execution

/**
 * Use this class to register components to be used at runtime / without the Equinox extension registry.
 */
class ExecutionRuntimeModule extends AbstractExecutionRuntimeModule {
	
	override bindILinkingService() {
		return ExecutionLinkingService
	}
	
}