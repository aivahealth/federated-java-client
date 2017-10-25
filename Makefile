build:
	mvn compile package

run: build
	java -cp target/federated-cli-1.0.jar com.aivahealth.federated.Main

deps:
	mvn dependency:copy-dependencies
