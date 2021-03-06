package fr.adrienbrault.idea.symfony2plugin.templating.annotation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.documentation.phpdoc.parser.PhpDocElementTypes;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpPsiElement;
import com.jetbrains.twig.TwigFile;
import fr.adrienbrault.idea.symfony2plugin.Symfony2ProjectComponent;
import fr.adrienbrault.idea.symfony2plugin.TwigHelper;
import fr.adrienbrault.idea.symfony2plugin.util.SymfonyBundleUtil;
import fr.adrienbrault.idea.symfony2plugin.util.dict.SymfonyBundle;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class AnnotationGoToDeclarationHandler implements GotoDeclarationHandler {

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(PsiElement psiElement, int i, Editor editor) {

        if(!Symfony2ProjectComponent.isEnabled(psiElement)) {
            return null;
        }

        if (!PlatformPatterns.psiElement(PhpDocElementTypes.DOC_TAG_NAME).withText(StandardPatterns.string().equalTo("@Template")).withLanguage(PhpLanguage.INSTANCE).accepts(psiElement)) {
            return null;
        }

        PsiElement phpDocTagValue = psiElement.getContext();
        if(!(phpDocTagValue instanceof PhpDocTag)) {
            return null;
        }

        // find template name on annotation parameter
        // @Template("templatename")
        PhpPsiElement phpDocAttrList = ((PhpDocTag) phpDocTagValue).getFirstPsiChild();
        if(phpDocAttrList == null) {
            return null;
        }

        String tagValue = phpDocAttrList.getText();
        Matcher matcher = Pattern.compile("\\(\"(.*)\"").matcher(tagValue);
        if (matcher.find()) {
            return TwigHelper.getTemplatePsiElements(psiElement.getProject(), matcher.group(1));
        }

        PhpDocComment docComment = PsiTreeUtil.getParentOfType(psiElement, PhpDocComment.class);
        if(null == docComment) {
            return null;
        }

        Method method = PsiTreeUtil.getNextSiblingOfType(docComment, Method.class);
        if(null == method) {
            return null;
        }

        String shortcutName = getTwigShortcutName(method);
        if(null == shortcutName) {
            return null;
        }

        return TwigHelper.getTemplatePsiElements(psiElement.getProject(), shortcutName);

    }

    @Nullable
    protected String getTwigShortcutName(Method method) {

        // indexAction
        String methodName = method.getName();
        if(!methodName.endsWith("Action")) {
            return null;
        }

        PhpClass phpClass = method.getContainingClass();
        if(null == phpClass) {
            return null;
        }

        // defaultController
        // default/Folder/FolderController
        String className = phpClass.getName();
        if(!className.endsWith("Controller")) {
            return null;
        }

        // find the bundle name of file
        SymfonyBundle bundle = getContainingBundle(phpClass);
        if(null == bundle) {
            return null;
        }

        // check if files is in <Bundle>/Controller/*
        if(!phpClass.getNamespaceName().startsWith(bundle.getNamespaceName() + "Controller\\")) {
            return null;
        }

        // strip the controller folder name
        String templateFolderName = phpClass.getNamespaceName().substring(bundle.getNamespaceName().length() + 11);

        // HomeBundle:default:indexes
        // HomeBundle:default/Test:indexes
        templateFolderName = templateFolderName.replace("\\", "/");
        String shortcutName = bundle.getName() + ":" + templateFolderName + className.substring(0, className.lastIndexOf("Controller")) + ":" + methodName.substring(0, methodName.lastIndexOf("Action"));

        // we should support types later on
        // HomeBundle:default:indexes.html.twig
        return shortcutName + ".html.twig";
    }

    @Nullable
    protected SymfonyBundle getContainingBundle(PhpClass phpClassSearch) {
        PhpIndex phpIndex = PhpIndex.getInstance(phpClassSearch.getProject());
        return new SymfonyBundleUtil(phpIndex).getContainingBundle(phpClassSearch);
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }
}
