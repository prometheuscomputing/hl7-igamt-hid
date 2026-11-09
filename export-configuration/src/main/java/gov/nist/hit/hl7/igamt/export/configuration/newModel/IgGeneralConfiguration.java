package gov.nist.hit.hl7.igamt.export.configuration.newModel;

public class IgGeneralConfiguration {

	private boolean notMessageInfrastructure;

	private ExampleMessageExportConfiguration exampleMessages;

	public boolean isNotMessageInfrastructure() {
		return notMessageInfrastructure;
	}

	public void setNotMessageInfrastructure(boolean notMessageInfrastructure) {
		this.notMessageInfrastructure = notMessageInfrastructure;
	}

	/**
	 * Never null: configurations saved before the option existed fall back to the defaults.
	 */
	public ExampleMessageExportConfiguration getExampleMessages() {
		if (exampleMessages == null) {
			exampleMessages = new ExampleMessageExportConfiguration();
		}
		return exampleMessages;
	}

	public void setExampleMessages(ExampleMessageExportConfiguration exampleMessages) {
		this.exampleMessages = exampleMessages;
	}

}
