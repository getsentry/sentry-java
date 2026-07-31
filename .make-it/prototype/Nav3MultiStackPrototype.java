import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public final class Nav3MultiStackPrototype {
  private static final String DEFAULT_STACK = "default";

  public static void main(String[] args) {
    Model model = Model.multiStack();
    Scanner scanner = new Scanner(System.in);

    while (true) {
      clear();
      System.out.println("Nav3 multiple-backstack context prototype");
      System.out.println();
      System.out.println(model.contextJson());
      System.out.println();
      System.out.println("Primary route: " + model.primaryRoute());
      System.out.println();
      System.out.println("[1] single stack  [2] multi stack  [3] cross-stack visible");
      System.out.println("[h] select home   [m] select mail  [p] push mail detail");
      System.out.println("[r] reset         [q] quit");

      String input = scanner.nextLine().trim();
      if ("q".equals(input)) {
        return;
      } else if ("1".equals(input)) {
        model = Model.singleStack();
      } else if ("2".equals(input)) {
        model = Model.multiStack();
      } else if ("3".equals(input)) {
        model = Model.crossStackVisible();
      } else if ("h".equals(input)) {
        model.selectedStack = "home";
        model.stacksInUse = Arrays.asList("home");
        model.visibleEntries = Arrays.asList(new VisibleEntry("home", "/Home"));
      } else if ("m".equals(input)) {
        model.selectedStack = "mail";
        model.stacksInUse = Arrays.asList("home", "mail");
        model.visibleEntries = Arrays.asList(new VisibleEntry("mail", model.topRoute("mail")));
      } else if ("p".equals(input)) {
        model.push("mail", "/Message");
        model.selectedStack = "mail";
        model.stacksInUse = Arrays.asList("home", "mail");
        model.visibleEntries = Arrays.asList(new VisibleEntry("mail", "/Message"));
      } else if ("r".equals(input)) {
        model = Model.multiStack();
      }
    }
  }

  private static void clear() {
    System.out.print("\033[2J\033[H");
    System.out.flush();
  }

  private static final class Model {
    String selectedStack;
    List<String> stacksInUse;
    final LinkedHashMap<String, List<String>> backstacks = new LinkedHashMap<>();
    List<VisibleEntry> visibleEntries = new ArrayList<>();

    static Model singleStack() {
      Model model = new Model();
      model.selectedStack = DEFAULT_STACK;
      model.stacksInUse = Arrays.asList(DEFAULT_STACK);
      model.backstacks.put(DEFAULT_STACK, new ArrayList<>(Arrays.asList("/Home", "/Profile")));
      model.visibleEntries = Arrays.asList(new VisibleEntry(DEFAULT_STACK, "/Profile"));
      return model;
    }

    static Model multiStack() {
      Model model = new Model();
      model.selectedStack = "mail";
      model.stacksInUse = Arrays.asList("home", "mail");
      model.backstacks.put("home", new ArrayList<>(Arrays.asList("/Home")));
      model.backstacks.put("mail", new ArrayList<>(Arrays.asList("/Inbox")));
      model.backstacks.put("settings", new ArrayList<>(Arrays.asList("/Settings")));
      model.visibleEntries = Arrays.asList(new VisibleEntry("mail", "/Inbox"));
      return model;
    }

    static Model crossStackVisible() {
      Model model = multiStack();
      model.visibleEntries =
          Arrays.asList(new VisibleEntry("home", "/Home"), new VisibleEntry("mail", "/Inbox"));
      return model;
    }

    void push(String stack, String route) {
      List<String> routes = backstacks.get(stack);
      if (routes != null && !routes.get(routes.size() - 1).equals(route)) {
        routes.add(route);
      }
    }

    String topRoute(String stack) {
      List<String> routes = backstacks.get(stack);
      return routes == null || routes.isEmpty() ? null : routes.get(routes.size() - 1);
    }

    String primaryRoute() {
      for (VisibleEntry entry : visibleEntries) {
        if (entry.stack.equals(selectedStack)) {
          return entry.route;
        }
      }
      if (!visibleEntries.isEmpty()) {
        return visibleEntries.get(visibleEntries.size() - 1).route;
      }
      return topRoute(selectedStack);
    }

    String contextJson() {
      StringBuilder out = new StringBuilder();
      out.append("{\n");
      out.append("  \"navigation\": {\n");
      out.append("    \"selected_stack\": \"").append(selectedStack).append("\",\n");
      out.append("    \"stacks_in_use\": ").append(stringArray(stacksInUse)).append(",\n");
      out.append("    \"backstacks\": [\n");
      int stackIndex = 0;
      for (Map.Entry<String, List<String>> entry : backstacks.entrySet()) {
        String name = entry.getKey();
        out.append("      {\n");
        out.append("        \"name\": \"").append(name).append("\",\n");
        out.append("        \"selected\": ").append(name.equals(selectedStack)).append(",\n");
        out.append("        \"in_use\": ").append(stacksInUse.contains(name)).append(",\n");
        out.append("        \"backstack\": ").append(routeEntries(entry.getValue())).append("\n");
        out.append("      }");
        if (++stackIndex < backstacks.size()) {
          out.append(",");
        }
        out.append("\n");
      }
      out.append("    ]");
      if (!visibleEntries.isEmpty()) {
        out.append(",\n");
        out.append("    \"visible_entries\": [\n");
        for (int i = 0; i < visibleEntries.size(); i++) {
          VisibleEntry entry = visibleEntries.get(i);
          out.append("      { \"stack\": \"")
              .append(entry.stack)
              .append("\", \"route\": \"")
              .append(entry.route)
              .append("\" }");
          if (i + 1 < visibleEntries.size()) {
            out.append(",");
          }
          out.append("\n");
        }
        out.append("    ]\n");
      } else {
        out.append("\n");
      }
      out.append("  }\n");
      out.append("}");
      return out.toString();
    }

    private static String stringArray(List<String> values) {
      StringBuilder out = new StringBuilder("[");
      for (int i = 0; i < values.size(); i++) {
        out.append('"').append(values.get(i)).append('"');
        if (i + 1 < values.size()) {
          out.append(", ");
        }
      }
      out.append(']');
      return out.toString();
    }

    private static String routeEntries(List<String> routes) {
      StringBuilder out = new StringBuilder("[");
      for (int i = 0; i < routes.size(); i++) {
        out.append("{ \"route\": \"").append(routes.get(i)).append("\" }");
        if (i + 1 < routes.size()) {
          out.append(", ");
        }
      }
      out.append(']');
      return out.toString();
    }
  }

  private static final class VisibleEntry {
    final String stack;
    final String route;

    VisibleEntry(String stack, String route) {
      this.stack = stack;
      this.route = route;
    }
  }
}
