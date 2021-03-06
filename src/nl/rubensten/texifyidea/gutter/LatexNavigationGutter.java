package nl.rubensten.texifyidea.gutter;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import nl.rubensten.texifyidea.TexifyIcons;
import nl.rubensten.texifyidea.lang.LatexNoMathCommand;
import nl.rubensten.texifyidea.lang.RequiredFileArgument;
import nl.rubensten.texifyidea.psi.LatexCommands;
import nl.rubensten.texifyidea.psi.LatexRequiredParam;
import nl.rubensten.texifyidea.util.FileUtilKt;
import nl.rubensten.texifyidea.util.TexifyUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static nl.rubensten.texifyidea.util.TexifyUtil.findFile;

/**
 * @author Ruben Schellekens
 */
public class LatexNavigationGutter extends RelatedItemLineMarkerProvider {

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            Collection<? super RelatedItemLineMarkerInfo> result) {
        // Only make markers when dealing with commands.
        if (!(element instanceof LatexCommands)) {
            return;
        }

        LatexCommands commands = (LatexCommands)element;
        PsiElement commandToken = commands.getCommandToken();
        if (commandToken == null) {
            return;
        }

        String fullCommand = commands.getCommandToken().getText();
        if (fullCommand == null) {
            return;
        }

        // True when it doesnt have a required file argument, but must be handled.
        boolean ignoreFileArgument = "\\RequirePackage".equals(fullCommand) ||
                "\\usepackage".equals(fullCommand);

        // Fetch the corresponding LatexNoMathCommand object.
        String commandName = fullCommand.substring(1);
        LatexNoMathCommand commandHuh = LatexNoMathCommand.get(commandName);
        if (commandHuh == null && !ignoreFileArgument) {
            return;
        }

        List<RequiredFileArgument> arguments = commandHuh.getArgumentsOf(RequiredFileArgument.class);
        if (arguments.isEmpty() && !ignoreFileArgument) {
            return;
        }

        // Get the required file arguments.
        RequiredFileArgument argument;
        if (ignoreFileArgument) {
            argument = new RequiredFileArgument("", "sty");
        }
        else {
            argument = arguments.get(0);
        }

        List<LatexRequiredParam> requiredParams = TexifyUtil.getRequiredParameters(commands);
        if (requiredParams.isEmpty()) {
            return;
        }

        // Make filename. Substring is to remove { and }.
        String fileName = requiredParams.get(0).getGroup().getText();
        fileName = fileName.substring(1, fileName.length() - 1);

        // Look up target file.
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) {
            return;
        }
        PsiDirectory containingDirectory = containingFile.getContainingDirectory();
        if (containingDirectory == null) {
            return;
        }

        List<VirtualFile> roots = new ArrayList<>();
        PsiFile rootFile = FileUtilKt.findRootFile(containingFile);
        roots.add(rootFile.getContainingDirectory().getVirtualFile());
        ProjectRootManager rootManager = ProjectRootManager.getInstance(element.getProject());
        Collections.addAll(roots, rootManager.getContentSourceRoots());

        VirtualFile file = null;
        for (VirtualFile root : roots) {
            Optional<VirtualFile> fileHuh = findFile(root, fileName, argument.getSupportedExtensions());
            if (fileHuh.isPresent()) {
                file = fileHuh.get();
                break;
            }
        }

        if (file == null) {
            return;
        }

        // Build gutter icon.
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(TexifyIcons.getIconFromExtension(file.getExtension()))
                .setTarget(PsiManager.getInstance(element.getProject()).findFile(file))
                .setTooltipText("Go to referenced file '" + file.getName() + "'");

        result.add(builder.createLineMarkerInfo(element));
    }
}
