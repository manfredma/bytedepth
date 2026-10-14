const fs = require("fs");
const path = require("path");

const template = fs.readFileSync(path.resolve(__dirname, "../../main/resources/templates/public/index.html"), "utf8");

describe("homepage reading entrances", () => {
  beforeEach(() => {
    document.body.innerHTML = template;
  });

  test("article and project counts link to their public lists", () => {
    const links = document.querySelectorAll(".bd-home-stats a");
    expect(links).toHaveLength(3);
    expect(links[0].getAttribute("th:href")).toBe("@{/posts}");
    expect(links[2].getAttribute("th:href")).toBe("@{/projects}");
    expect(links[0].querySelector("b").getAttribute("th:text")).toBe("${total}");
    expect(links[2].querySelector("b").getAttribute("th:text")).toBe("${#lists.size(projects)}");
  });

  test("topic count targets the actual category browser rather than columns", () => {
    const link = document.querySelectorAll(".bd-home-stats a")[1];
    expect(link).toBeDefined();
    expect(link.getAttribute("href")).toBe("#home-topics");
    expect(document.querySelector(link.getAttribute("href")).textContent).toContain("按分类浏览");
    expect(link.querySelector("b").getAttribute("th:text")).toBe("${#lists.size(allCategories)}");
  });

  test("a missing editor pick adds no empty placeholder between hero and feed", () => {
    const feature = document.querySelector(".bd-home-feature");
    expect(feature.getAttribute("th:if")).toBe("${featuredPost != null}");
    feature.remove();
    expect(document.querySelector(".bd-home-hero").nextElementSibling.classList.contains("bd-home-recent")).toBe(true);
    expect(document.body.textContent).not.toContain("暂时没有主推文章");
  });
});
