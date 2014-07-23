package pro.advantis.java;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import net.sourceforge.pmd.cpd.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class OCPDInspection extends GlobalInspectionTool
{
    @Override
    public void runInspection(final AnalysisScope scope,
                              final InspectionManager manager,
                              final GlobalInspectionContext globalContext,
                              final ProblemDescriptionsProcessor problemDescriptionsProcessor)
    {
        final CPD detector = new CPD(100, new ObjectivecLanguage());

        final RefProject project = globalContext.getRefManager().getRefProject();

        final HashMap<String, PsiFile> map = new HashMap<String, PsiFile>(scope.getFileCount());
        scope.accept(new PsiElementVisitor()
        {
            @Override
            public void visitFile(PsiFile file)
            {
                VirtualFile virtualFile = file.getVirtualFile();
                if (null != virtualFile)
                {
                    String path = virtualFile.getPath();
                    map.put(path, file);
                    try
                    {
                        detector.add(new File(path));
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        });

        detector.go();

        ApplicationManager.getApplication().runReadAction(new Runnable()
        {
            public void run()
            {
                Iterator<Match> iterator = detector.getMatches();
                while (iterator.hasNext())
                {
                    Match match = iterator.next();
                    Set<TokenEntry> marks = match.getMarkSet();

                    for (TokenEntry entry : marks)
                    {
                        PsiFile file = map.get(entry.getTokenSrcID());
                        if (null != file)
                        {
                            Document doc = PsiDocumentManager.getInstance(globalContext.getProject()).getDocument(file);
                            String description = String.format("%d Copy-Pasted \nlines from ", match.getLineCount());

                            final int size = marks.size();
                            ArrayList<String> files = new ArrayList<String>(size);
                            ArrayList<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>(size);

                            for (TokenEntry other : marks)
                            {
                                if (other == entry) continue;

                                PsiFile otherFile = map.get(other.getTokenSrcID());
                                files.add(String.format("%s:%d", otherFile.getName(), other.getBeginLine()));

                                PsiElement element = file.findElementAt(doc.getLineStartOffset(other.getBeginLine()));
                                fixes.add(new NavigateAction(element, other));
                            }

                            description += join(files, ", ");

                            LocalQuickFix[] allFixes = new LocalQuickFix[fixes.size()];
                            fixes.toArray(allFixes);

                            PsiElement startElement = file.findElementAt(doc.getLineStartOffset(entry.getBeginLine()));
                            CommonProblemDescriptor descriptor = manager.createProblemDescriptor(startElement, description, false, allFixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                            problemDescriptionsProcessor.addProblemElement(globalContext.getRefManager().getReference(file), descriptor);
                        }
                    }
                }
            }
        });
    }

    private String join(Iterable<String> elements, String delimiter)
    {
        Iterator<String> iterator = elements.iterator();
        if (!iterator.hasNext()) return "";
        StringBuilder buffer = new StringBuilder(iterator.next());
        while (iterator.hasNext()) buffer.append(delimiter).append(iterator.next());
        return buffer.toString();
    }

    private class NavigateAction implements LocalQuickFix
    {
        private PsiElement element;
        private TokenEntry mark;

        private NavigateAction(PsiElement element, TokenEntry mark)
        {
            this.element = element;
            this.mark = mark;
        }

        @NotNull
        @Override
        public String getName()
        {
            return String.format("View %s:%d", element.getContainingFile().getName(), mark.getBeginLine());
        }

        @NotNull
        @Override
        public String getFamilyName()
        {
            return "View";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)
        {
            navigate(element);
        }

        private void navigate(PsiElement element)
        {
            VirtualFile file = element.getNavigationElement().getContainingFile().getVirtualFile();
            if (null != file)
            {
                Project project = element.getProject();
                OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, element.getTextOffset());
                FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
                fileEditorManager.openTextEditor(descriptor, true);
            }
        }

        /**
         * Alternative approach
         */
        private void altNavigate(PsiElement element)
        {
            PsiElement navigationElement = element.getNavigationElement();
            if (null != navigationElement && navigationElement instanceof Navigatable)
            {
                Navigatable navigatable = (Navigatable)navigationElement;
                if (navigatable.canNavigate())
                {
                    navigatable.navigate(true);
                }
            }
        }
    }

//    @Override
//    public JComponent createOptionsPanel() {
//        final JPanel result = new JPanel(new BorderLayout());
//
//        final JPanel internalPanel = new JPanel(new BorderLayout());
//        result.add(internalPanel, BorderLayout.NORTH);
//
//        final FieldPanel additionalAttributesPanel = new FieldPanel(null, "Title", null, null);
//        additionalAttributesPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
//            @Override
//            protected void textChanged(DocumentEvent e) {
//                final javax.swing.text.Document document = e.getDocument();
//                try {
//                    final String text = document.getText(0, document.getLength());
//                }
//                catch (BadLocationException e1) {
//                }
//            }
//        });
//
//        final JCheckBox checkBox = new JCheckBox("Checkbox");
//        checkBox.setSelected(true);
//        checkBox.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                final boolean b = checkBox.isSelected();
//                additionalAttributesPanel.setEnabled(b);
//            }
//        });
//
//        internalPanel.add(checkBox, BorderLayout.NORTH);
//        internalPanel.add(additionalAttributesPanel, BorderLayout.CENTER);
//
//        additionalAttributesPanel.setPreferredSize(new Dimension(150, additionalAttributesPanel.getPreferredSize().height));
//        additionalAttributesPanel.setEnabled(true);
//        additionalAttributesPanel.setText("Some other");
//
//        return result;
//    }

    @Override
    public boolean isGraphNeeded()
    {
        return false;
    }
}
