/*
 * Written by Brian de Alwis.
 * Released under the <a href="http://unlicense.org">UnLicense</a>
 */
package ca.mt.wb.devtools.tx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.ui.IJavaElementSearchConstants;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.dialogs.SelectionDialog;
import org.eclipse.ui.part.IShowInTarget;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.zest.core.viewers.GraphViewer;
import org.eclipse.zest.core.widgets.ZestStyles;
import org.eclipse.zest.layouts.LayoutAlgorithm;
import org.eclipse.zest.layouts.LayoutStyles;
import org.eclipse.zest.layouts.algorithms.DirectedGraphLayoutAlgorithm;
import org.eclipse.zest.layouts.algorithms.HorizontalTreeLayoutAlgorithm;
import org.eclipse.zest.layouts.algorithms.RadialLayoutAlgorithm;
import org.eclipse.zest.layouts.algorithms.SpringLayoutAlgorithm;
import org.eclipse.zest.layouts.algorithms.TreeLayoutAlgorithm;

public class InheritanceView extends ViewPart implements IShowInTarget {

	public static String viewID = "ca.mt.wb.devtools.tx.view";

	private GraphViewer viewer;
	private Action openInEditorAction;
	private Action addNodeAction;
	private Action deleteNodeAction;
	private Action clearAction;
	private Action expandElementAction;

	private List<IAction> layoutActions;

	// private ZESTImages images;

	/**
	 * The constructor.
	 */
	public InheritanceView() {
		// this.images = new ZESTImages();
	}

	/**
	 * This is a callback that will allow us to create the viewer and initialize
	 * it.
	 */
	public void createPartControl(Composite parent) {
		// ((WorkbenchWindow)getViewSite().getWorkbenchWindow()).setCoolBarVisible(true);
		// ((ToolBarManager)((WorkbenchPage)getSite().getPage()).getActionBars().getToolBarManager()).
		viewer = new GraphViewer(parent, ZestStyles.NONE); /*
															 * , ZestStyles.
															 * NODES_HIGHLIGHT_ADJACENT
															 * /*| ZESTStyles.
															 * MARQUEE_SELECTION
															 */
		viewer.setNodeStyle(ZestStyles.NODES_CACHE_LABEL);
		viewer.setLayoutAlgorithm(new TreeLayoutAlgorithm(
				LayoutStyles.NO_LAYOUT_NODE_RESIZING));
		// viewer.setContentProvider(new GraphContentProvider() );
		viewer.setContentProvider(new IVTypeProvider(viewer));
		viewer.setLabelProvider(new IVLabelProvider(viewer)); // new
																// JavaElementLabelProvider()
		viewer.setInput(new ComparisonModel());

		makeActions();
		contributeToActionBars();
		hookContextMenu();
		hookDoubleClickAction();
		configureDropSupport();
		getSite().setSelectionProvider(viewer);

		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				List<IType> selectedTypes = getSelectedTypes();
				deleteNodeAction.setEnabled(!selectedTypes.isEmpty());
				expandElementAction.setEnabled(!selectedTypes.isEmpty());
				openInEditorAction.setEnabled(selectedTypes.size() == 1);
			}
		});
	}

	private void hookDoubleClickAction() {
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				openInEditorAction.run();
			}
		});
	}

	private void configureDropSupport() {
		Transfer transfers[] = { TextTransfer.getInstance(),
				JavaUI.getJavaElementClipboardTransfer(),
				LocalSelectionTransfer.getTransfer() };
		System.out.println("InheritanceView: Adding drop support");
		// DND.DROP_DEFAULT
		viewer.addDropSupport(DND.DROP_COPY | DND.DROP_LINK | DND.DROP_DEFAULT,
				transfers, new InheritanceViewerDropAdapter(viewer, transfers));
	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}

	protected List<IType> getSelectedTypes() {
		ISelection selection = viewer.getSelection();
		if (selection.isEmpty() || !(selection instanceof IStructuredSelection)) {
			return Collections.emptyList();
		}
		List<IType> selectedTypes = new ArrayList<IType>();
		for (Object o : ((IStructuredSelection) selection).toArray()) {
			if (o instanceof IType) {
				selectedTypes.add((IType) o);
			}
		}
		return selectedTypes;
	}

	public Viewer getViewer() {
		return viewer;
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
		bars.setGlobalActionHandler(ActionFactory.DELETE.getId(),
				deleteNodeAction);
		bars.updateActionBars();
	}

	private void fillLocalPullDown(IMenuManager manager) {
		// manager.add(addNodeAction);
		// //manager.add(deleteNodeAction);
		// manager.add(new Separator());
		// manager.add(pauseAction);
		// manager.add(resumeAction);
		// manager.add(new Separator());
		// manager.add(stopAction);
		// manager.add(restartAction);
		// manager.add(new Separator());
		for (IAction action : layoutActions) {
			manager.add(action);
		}
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(addNodeAction);
		manager.add(deleteNodeAction);
		manager.add(clearAction);
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	private void fillContextMenu(IMenuManager manager) {
		if (!getSelectedTypes().isEmpty()) {
			manager.add(expandElementAction);
			manager.add(deleteNodeAction);
		}

		// Other plug-ins can contribute there actions here
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	/**
	 * Creates the menu and toolbar actions.
	 */
	private void makeActions() {

		addNodeAction = new Action() {
			public void run() {
				try {
					SelectionDialog dialog = JavaUI.createTypeDialog(getSite()
							.getShell(), new ProgressMonitorDialog(getSite()
							.getShell()), SearchEngine.createWorkspaceScope(),
							IJavaElementSearchConstants.CONSIDER_ALL_TYPES,
							true);
					dialog.setTitle("Add type(s)...");
					dialog.setMessage("Type(s) to add?");
					if (dialog.open() == IDialogConstants.CANCEL_ID) {
						return;
					}
					Object[] types = dialog.getResult();
					if (types == null || types.length == 0) {
						return;
					}
					for (int i = 0; i < types.length; i++) {
						addType((IType) types[i]);
					}
				} catch (JavaModelException e) {
					/* do nothing */
				}
			}
		};

		addNodeAction.setText("Add Type");
		addNodeAction.setToolTipText("Add a new class or interface");
		addNodeAction.setImageDescriptor(getSharedImages().getImageDescriptor(
				ISharedImages.IMG_OBJ_ADD));

		expandElementAction = new Action() {
			public void run() {
				for (IType t : getSelectedTypes()) {
					addSubTypes(t);
				}
			}
		};
		expandElementAction.setText("Add Subtypes");
		expandElementAction.setToolTipText("Add subtypes of selected element");
		expandElementAction.setImageDescriptor(getSharedImages()
				.getImageDescriptor(ISharedImages.IMG_OBJ_ADD));

		// create the action that deletes the selected node
		deleteNodeAction = new Action() {
			public void run() {
				// ISelection = viewer.getSelection();
				ComparisonModel model = ((ComparisonModel) viewer.getInput())
						.copy();
				boolean modified = false;
				for (IType t : getSelectedTypes()) {
					if (model.remove(t)) {
						modified = true;
					}
				}
				if (modified) {
					viewer.setInput(model);
				}
				setEnabled(false);
			}
		};
		deleteNodeAction.setEnabled(false);
		deleteNodeAction.setText("Delete Node");
		deleteNodeAction.setToolTipText("Delete the selected node");
		deleteNodeAction.setImageDescriptor(getSharedImages()
				.getImageDescriptor(ISharedImages.IMG_ETOOL_DELETE));
		deleteNodeAction.setDisabledImageDescriptor(getSharedImages()
				.getImageDescriptor(ISharedImages.IMG_ETOOL_DELETE_DISABLED));

		clearAction = new Action() {
			public void run() {
				viewer.setInput(new ComparisonModel());
				// viewer.refresh();
			}
		};
		clearAction.setText("Clear");
		clearAction.setToolTipText("Remove all elements");
		clearAction.setImageDescriptor(getSharedImages().getImageDescriptor(
				ISharedImages.IMG_ETOOL_CLEAR));
		clearAction.setDisabledImageDescriptor(getSharedImages()
				.getImageDescriptor(ISharedImages.IMG_ETOOL_CLEAR_DISABLED));

		openInEditorAction = new Action() {
			public void run() {
				openInEditor(getSelectedTypes().get(0));
			}
		};

		layoutActions = new ArrayList<IAction>();
		layoutActions
				.add(createLayoutAction("Spring", new SpringLayoutAlgorithm(
						LayoutStyles.NO_LAYOUT_NODE_RESIZING)));
		layoutActions
				.add(createLayoutAction("Radial", new RadialLayoutAlgorithm(
						LayoutStyles.NO_LAYOUT_NODE_RESIZING)));
		layoutActions.add(createLayoutAction("Directed",
				new DirectedGraphLayoutAlgorithm(
						LayoutStyles.NO_LAYOUT_NODE_RESIZING)));
		layoutActions.add(createLayoutAction("Tree", new TreeLayoutAlgorithm(
				LayoutStyles.NO_LAYOUT_NODE_RESIZING)));
		layoutActions.add(createLayoutAction("Horizontal Tree",
				new HorizontalTreeLayoutAlgorithm(
						LayoutStyles.NO_LAYOUT_NODE_RESIZING)));

	}

	private IAction createLayoutAction(String desc, final LayoutAlgorithm la) {
		Action action = new Action() {
			public void run() {
				viewer.setLayoutAlgorithm(la, true);
				viewer.refresh();
			}
		};
		action.setText(desc);
		action.setToolTipText("Re-layout items with " + desc);
		return action;
	}

	private ISharedImages getSharedImages() {
		return getSite().getWorkbenchWindow().getWorkbench().getSharedImages();
	}

	protected void openInEditor(Object obj) {
		try {
			if (obj instanceof IJavaElement) {
				IJavaElement je = (IJavaElement) obj;
				IEditorPart editor = JavaUI.openInEditor(je);
				JavaUI.revealInEditor(editor, je);
			}
		} catch (JavaModelException e) {
			/* ignore */
		} catch (PartInitException e) {
			/* ignore */
		}
	}

	protected void addType(IType type) {
		ComparisonModel model = ((ComparisonModel) viewer.getInput()).copy();
		model.add(type);
		viewer.setInput(model);
		// viewer.refresh();
	}

	public void addTypes(Collection<IType> types) {
		ComparisonModel model = ((ComparisonModel) viewer.getInput()).copy();
		for (IType t : types) {
			model.add(t);
		}
		viewer.setInput(model);
	}

	protected void addSubTypes(final IType type) {
		Job job = new Job("Add Subtypes") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				monitor.beginTask(
						"Adding subtypes of " + type.getElementName(), 100);
				try {
					monitor.subTask("Computing type hierarchy");
					ITypeHierarchy hierarchy = type
							.newTypeHierarchy(new SubProgressMonitor(monitor,
									90));
					monitor.subTask("Adding elements");
					final Collection<IType> subtypes = Arrays.asList(hierarchy
							.getSubtypes(type));
					monitor.worked(10);
					getDisplay().asyncExec(new Runnable() {
						public void run() {
							addTypes(subtypes);
						}
					});
				} catch (JavaModelException e) {
					return new Status(IStatus.ERROR, "ca.mt.wb.devtools.tx",
							"Error occurred computing type hierarchy for "
									+ type.getElementName(), e);
				} finally {
					monitor.done();
				}
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}

	protected Display getDisplay() {
		return viewer.getControl().getDisplay();
	}

	public boolean show(ShowInContext context) {
		Collection<IType> types = OpenTypesExplorerHandler.adapt(
				context.getSelection(), IType.class);
		if (!types.isEmpty()) {
			addTypes(types);
			return true;
		}
		IType t = OpenTypesExplorerHandler.adapt(context.getInput(),
				IType.class);
		if (t != null) {
			addType(t);
			return true;
		}
		return false;
	}
}
