/*********************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lucas Bullen (Red Hat Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.ppp4e.ui.wizard;

import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.ppp4e.ProvisioningPlugin;
import org.eclipse.ppp4e.core.Server;
import org.eclipse.ppp4j.messages.ComponentVersion;
import org.eclipse.ppp4j.messages.ComponentVersionSelection;
import org.eclipse.ppp4j.messages.ErroneousParameter;
import org.eclipse.ppp4j.messages.InitializeResult;
import org.eclipse.ppp4j.messages.ProvisioningParameters;
import org.eclipse.ppp4j.messages.Template;
import org.eclipse.ppp4j.messages.TemplateSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Text;

public class NewProjectWizardPage extends WizardPage {
	private File directory;
	private InitializeResult initializeResult;
	private Server server;
	private Semaphore initSemaphore = new Semaphore(0);

	private Label loadingLabel;
	private String loadingLabelText = "Loading wizard from Project Provisioning Server...";

	private Text nameInput;
	private Text locationInput;
	private Text versionInput;

	private ControlDecoration nameDecoration;
	private ControlDecoration locationDecoration;
	private ControlDecoration versionDecoration;
	private ControlDecoration templateDecoration;
	// TODO: private ControlDecoration componentVersionDecoration;
	// TODO: private ControlDecoration templateComponentVersionDecoration;

	private ProvisioningParameters parameters;

	protected NewProjectWizardPage(String pageName) {
		super(pageName);
	}

	public ProvisioningParameters getParameters() {
		parameters.name = nameInput.getText();
		parameters.location = locationInput.getText();
		if (versionInput != null) {
			parameters.version = versionInput.getText();
		}
		return parameters;
	}

	public void updatedButtons() {
		Display.getDefault().asyncExec(() -> {
			getContainer().updateButtons();
		});
	}

	@Override
	public void createControl(Composite parent) {
		CompletableFuture.runAsync(() -> {
			try {
				initSemaphore.acquire();
				Display.getDefault().asyncExec(() -> {
					if (initializeResult != null) {
						removeLoadingControl();
						createParameterControl(parent);
					} else {
						loadingLabelText = "Failed to start the provisioning server.";
						if (loadingLabel != null) {
							loadingLabel.setText(loadingLabelText);
						}
					}
				});
			} catch (InterruptedException e) {
				ProvisioningPlugin.logError(e);
				getWizard().performCancel();
			}
		});
		addLoadingControl(parent);
	}

	private void addLoadingControl(Composite parent) {
		Composite container = (Composite) getControl();
		if (container == null) {
			container = new Composite(parent, SWT.NULL);
			setControl(container);
		}
		if (initializeResult != null) {
			return;
		}
		container.setLayout(new GridLayout(1, false));
		loadingLabel = new Label(container, SWT.NONE);
		loadingLabel.setText(loadingLabelText);
		if (initializeResult != null) {
			removeLoadingControl();
		}
		setPageComplete(false);
	}

	private void removeLoadingControl() {
		if (loadingLabel != null && !loadingLabel.isDisposed()) {
			loadingLabel.dispose();
		}
	}

	private void createParameterControl(Composite parent) {
		Image errorImage = FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR)
				.getImage();

		Composite container = (Composite) getControl();
		if (container == null) {
			container = new Composite(parent, SWT.NULL);
			setControl(container);
		}
		if (container.isDisposed()) {
			return;
		}
		container.setLayout(new GridLayout(3, false));

		Label nameLabel = new Label(container, SWT.NONE);
		nameLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		nameLabel.setText("Name:");
		nameInput = new Text(container, SWT.BORDER);
		nameInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		nameInput.setText(initializeResult.defaultProvisioningParameters.name);
		nameInput.addModifyListener(e -> {
			validate();
		});
		nameDecoration = new ControlDecoration(nameInput, SWT.TOP | SWT.LEFT);
		nameDecoration.setImage(errorImage);
		new Label(container, SWT.NONE);

		Label locationLabel = new Label(container, SWT.NONE);
		locationLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		locationLabel.setText("Location:");
		locationInput = new Text(container, SWT.BORDER);
		locationInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		locationInput.addModifyListener(e -> {
			validate();
		});
		locationDecoration = new ControlDecoration(locationInput, SWT.TOP | SWT.LEFT);
		locationDecoration.setImage(errorImage);
		String location = initializeResult.defaultProvisioningParameters.location;
		if (location == null || location.isEmpty()) {
			locationInput.setText(directory.getAbsolutePath());
		} else {
			locationInput.setText(location);
		}
		Button browseButton = new Button(container, SWT.NONE);
		browseButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		browseButton.setText("Browse");
		browseButton.addSelectionListener(widgetSelectedAdapter(e -> {
			DirectoryDialog dialog = new DirectoryDialog(browseButton.getShell());
			String path = dialog.open();
			if (path != null) {
				locationInput.setText(path);
			}
		}));

		if (initializeResult.versionRequired) {
			Label versionLabel = new Label(container, SWT.NONE);
			versionLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
			versionLabel.setText("Version:");
			versionInput = new Text(container, SWT.BORDER);
			versionInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
			versionInput.setText(initializeResult.defaultProvisioningParameters.version);
			versionInput.addModifyListener(e -> {
				validate();
			});
			versionDecoration = new ControlDecoration(versionInput, SWT.TOP | SWT.LEFT);
			versionDecoration.setImage(errorImage);
			new Label(container, SWT.NONE);
		}

		new Label(container, SWT.NONE);
		createTemplatesControl(container, initializeResult.templates);
		new Label(container, SWT.NONE);

		new Label(container, SWT.NONE);
		createComponentVersionsControl(container, initializeResult.componentVersions,
				parameters.componentVersionSelections);
		container.getParent().layout(true, true);
		container.redraw();
		container.update();
		getShell().pack(true);
		showError(null, null);
	}

	private void createTemplatesControl(Composite container, Template[] templates) {
		Image errorImage = FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR)
				.getImage();
		Group templateContainer = new Group(container, SWT.BORDER);
		templateContainer.setLayout(new GridLayout(1, false));
		templateContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		templateContainer.setText("Templates");

		List list = new List(templateContainer, SWT.V_SCROLL | SWT.BORDER);
		GridData listBoxData = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		list.setLayoutData(listBoxData);
		ListViewer templateViewer = new ListViewer(list);
		templateViewer.setContentProvider(new ArrayContentProvider());
		templateViewer.setComparator(new ViewerComparator());
		templateViewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				Template template = (Template) element;
				return template.title;
			}
		});
		templateDecoration = new ControlDecoration(templateViewer.getControl(), SWT.TOP | SWT.LEFT);
		templateDecoration.setImage(errorImage);
		Composite componentContainer = new Composite(templateContainer, SWT.NULL);
		componentContainer.setLayout(new GridLayout(1, false));
		templateViewer.addSelectionChangedListener(e -> {
			for (Control control : componentContainer.getChildren()) {
				control.dispose();
			}
			Object selection = e.getStructuredSelection().getFirstElement();
			if (selection != null && selection instanceof Template) {
				setTemplate(((Template) selection));
				createComponentVersionsControl(componentContainer, ((Template) selection).componentVersions,
						parameters.templateSelection.componentVersions);
				componentContainer.getParent().layout(true, true);
				componentContainer.getParent().getParent().getParent().getParent().layout(true, true);
				componentContainer.getParent().getParent().getParent().getParent().redraw();
				componentContainer.getParent().getParent().getParent().getParent().update();
				getShell().pack(true);
			}
			validate();
		});
		templateViewer.setInput(templates);
		templateViewer.setSelection(new StructuredSelection(templateViewer.getElementAt(0)), true);
	}

	private void setTemplate(Template template) {
		parameters.templateSelection.id = template.id;
		parameters.templateSelection.componentVersions = new ComponentVersionSelection[template.componentVersions.length];
		for (int i = 0; i < template.componentVersions.length; i++) {
			ComponentVersion componentVersion = template.componentVersions[i];
			if (componentVersion.versions.length == 0) {
				continue;
			}
			parameters.templateSelection.componentVersions[i] = new ComponentVersionSelection(componentVersion.id,
					componentVersion.versions[0].id);
		}
	}

	private void createComponentVersionsControl(Composite container, ComponentVersion[] componentVersions,
			ComponentVersionSelection[] selections) {
		Composite componentContainer = new Composite(container, SWT.NONE);
		componentContainer.setLayout(new GridLayout(2, false));
		componentContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

		for (ComponentVersion componentVersion : componentVersions) {
			Label label = new Label(componentContainer, SWT.NONE);
			label.setText(componentVersion.title);
			label.setToolTipText(componentVersion.caption);
			label.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));

			Combo combo = new Combo(componentContainer, SWT.READ_ONLY);
			combo.setToolTipText(componentVersion.caption);
			String[] verisonLabels = new String[componentVersion.versions.length];
			for (int i = 0; i < componentVersion.versions.length; i++) {
				verisonLabels[i] = componentVersion.versions[i].title;
			}
			combo.setItems(verisonLabels);
			combo.select(0);
			combo.addSelectionListener(widgetSelectedAdapter(e -> {
				for (ComponentVersionSelection selection : selections) {
					if (selection.id.equals(componentVersion.id)) {
						selection.versionId = combo.getText();
					}
				}
				validate();
			}));
		}
		componentContainer.getParent().layout(true, true);
		componentContainer.redraw();
		componentContainer.update();
	}

	public void setDirectory(File directory) {
		this.directory = directory;
	}

	public void init(InitializeResult initializeResult, Server server) {
		this.initializeResult = initializeResult;
		this.server = server;
		if (initializeResult != null) {
			parameters = new ProvisioningParameters();
			parameters.templateSelection = new TemplateSelection();
			parameters.componentVersionSelections = new ComponentVersionSelection[initializeResult.componentVersions.length];
			for (int i = 0; i < initializeResult.componentVersions.length; i++) {
				ComponentVersion componentVersion = initializeResult.componentVersions[i];
				if (componentVersion.versions.length == 0) {
					continue;
				}
				parameters.componentVersionSelections[i] = new ComponentVersionSelection(componentVersion.id,
						componentVersion.versions[0].id);
			}
		}
		initSemaphore.release();
	}

	private void validate() {
		if (server != null) {
			server.Validation(getParameters()).thenAccept(result -> {
				boolean isPageComplete = !showError(result.errorMessage, result.erroneousParameters);
				Display.getDefault().asyncExec(() -> {
					setPageComplete(isPageComplete);
				});
			});
		}
	}

	public boolean showError(String errorMessage, ErroneousParameter[] erroneousParameters) {
		Display.getDefault().asyncExec(() -> {
			setErrorMessage(errorMessage);
			nameDecoration.hide();
			locationDecoration.hide();
			if (versionDecoration != null) {
				versionDecoration.hide();
			}
			templateDecoration.hide();
		});

		if (erroneousParameters == null || erroneousParameters.length == 0) {
			return errorMessage != null && !errorMessage.isEmpty();
		}
		for (ErroneousParameter erroneousParameter : erroneousParameters) {
			if (erroneousParameter == null) {
				continue;
			}
			Display.getDefault().asyncExec(() -> {
				switch (erroneousParameter.parameterType) {
				case Name:
					nameDecoration.showHoverText(erroneousParameter.message);
					nameDecoration.show();
					break;
				case Location:
					locationDecoration.showHoverText(erroneousParameter.message);
					locationDecoration.show();
					break;
				case Version:
					if (versionDecoration != null) {
						versionDecoration.showHoverText(erroneousParameter.message);
						versionDecoration.show();
					}
					break;
				case Template:
					templateDecoration.showHoverText(erroneousParameter.message);
					templateDecoration.show();
					break;
				default:
					break;
				}
			});
		}
		return true;
	}

}
