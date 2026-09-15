(function() {
        var slogans = [
            { quote: "Talk is cheap. Show me the code.", author: "— Linus Torvalds" },
            { quote: "任何傻瓜都能写出计算机能理解的代码，优秀的程序员写出人类能理解的代码。", author: "— Martin Fowler" },
            { quote: "过早的优化是万恶之源。", author: "— Donald Knuth" },
            { quote: "Make it work, make it right, make it fast.", author: "— Kent Beck" },
            { quote: "深入字节与比特，探索性能与优雅的边界。", author: "— bytedepth" },
            { quote: "Programs must be written for people to read, and only incidentally for machines to execute.", author: "— Harold Abelson" },
            { quote: "Simplicity is the soul of efficiency.", author: "— Austin Freeman" },
            { quote: "First, solve the problem. Then, write the code.", author: "— John Johnson" },
            { quote: "简单是可靠性的前提。", author: "— Edsger W. Dijkstra" },
            { quote: "The best code is no code at all.", author: "— Jeff Atwood" },
            { quote: "Debugging is twice as hard as writing the code in the first place.", author: "— Brian W. Kernighan" },
            { quote: "好的架构让正确的事情变得容易，让错误的事情变得困难。", author: "— 软件工程智慧" },
            { quote: "Don't Repeat Yourself.", author: "— Andy Hunt & Dave Thomas" },
            { quote: "一千行可以工作的代码，胜过一万行无法维护的代码。", author: "— bytedepth" },
            { quote: "测量，而不是猜测。", author: "— 性能优化原则" },
            { quote: "You ain't gonna need it.", author: "— YAGNI Principle" },
            { quote: "代码是写给人读的，机器执行只是副产品。", author: "— 程序员箴言" },
            { quote: "每一次重构，都是对未来自己的一封善意的信。", author: "— bytedepth" },
            { quote: "Architecture is about the important stuff, whatever that is.", author: "— Ralph Johnson" },
            { quote: "每一行代码背后，都是一个值得深思的决策。", author: "— bytedepth" },
            { quote: "单一职责：一个类，一个理由去改变。", author: "— SOLID 原则" },
            { quote: "开闭原则：对扩展开放，对修改关闭。", author: "— Bertrand Meyer" },
            { quote: "接口隔离：胖接口是万恶之源。", author: "— SOLID 原则" },
            { quote: "依赖反转：依赖抽象，而非具体实现。", author: "— Robert C. Martin" },
            { quote: "里氏替换：子类应能替换父类而不改变正确性。", author: "— Barbara Liskov" },
            { quote: "KISS：Keep It Simple, Stupid.", author: "— 设计格言" },
            { quote: "高内聚、低耦合，模块化的永恒追求。", author: "— 软件设计原则" },
            { quote: "关注点分离：每个模块只关心一件事。", author: "— Edsger W. Dijkstra" },
            { quote: "约定优于配置，减少决策疲劳。", author: "— 框架设计原则" },
            { quote: "Fail fast：让错误尽早暴露，而不是沉默地蔓延。", author: "— 系统设计原则" },
            { quote: "防御性编程：信任不信任，校验一切。", author: "— 安全编程原则" },
            { quote: "Command Query Separation：改不返回，查不修改。", author: "— Bertrand Meyer" },
            { quote: "Tell, Don't Ask：告诉对象做什么，别问它要数据。", author: "— 面向对象原则" },
            { quote: "Law of Demeter：只跟你的直接朋友说话。", author: "— 最小知识原则" },
            { quote: "Composition over Inheritance：组合优于继承。", author: "— GoF 设计模式" },
            { quote: "Program to an interface, not an implementation.", author: "— GoF 设计模式" },
            { quote: "分层架构：每一层都是它下面的抽象。", author: "— 架构原则" },
            { quote: "依赖方向：从外向内，依赖倒置。", author: "— 整洁架构" },
            { quote: "边界：架构的核心是划定边界。", author: "— Robert C. Martin" },
            { quote: "测试金字塔：多单元，少集成，偶 E2E。", author: "— 测试策略" },
            { quote: "写测试不是为了证明代码正确，而是为了敢于修改。", author: "— 测试驱动开发" },
            { quote: "Red, Green, Refactor — TDD 的铁三角。", author: "— Kent Beck" },
            { quote: "凡是不变的，就提取出来；凡是会变的，就封装起来。", author: "— 设计原则" },
            { quote: "Single source of truth：每一条数据只有一个权威来源。", author: "— 数据架构原则" },
            { quote: "最终一致性不是“不一致”，而是“暂未一致”。", author: "— 分布式系统格言" },
            { quote: "分布式系统第一谬误：网络是可靠的。", author: "— Peter Deutsch" },
            { quote: "缓存是计算机科学中唯一真正困难的两件事之一。", author: "— Phil Karlton" },
            { quote: "命名是计算机科学中最困难的两件事之一。", author: "— Phil Karlton" },
            { quote: "没有银弹：没有一种技术能十倍提升软件生产率。", author: "— Fred Brooks" },
            { quote: "八二定律：80% 的时间花在 20% 的代码上。", author: "— 软件工程经验" },
            { quote: "Brooks 法则：给一个延期的项目加人，只会让它更延期。", author: "— Fred Brooks" },
            { quote: "康威定律：系统的架构拷贝组织的沟通结构。", author: "— Melvin Conway" },
            { quote: "好名字不需要注释。", author: "— 代码整洁之道" },
            { quote: "函数应该只做一件事，并且做好。", author: "— Robert C. Martin" },
            { quote: "删除代码是比添加代码更难掌握的技能。", author: "— 软件工程智慧" },
            { quote: "能用枚举解决的问题，不要用字符串。", author: "— 类型安全原则" },
            { quote: "不可变对象永不让你失望。", author: "— 并发编程格言" },
            { quote: "乐观锁解决冲突，悲观锁预防冲突。", author: "— 并发控制原则" },
            { quote: "幂等性是分布式系统安全的基石。", author: "— 系统设计原则" },
            { quote: "限流让系统有尊严地过载，而不是崩溃。", author: "— 服务治理原则" },
            { quote: "熔断是给系统一个优雅恢复的机会。", author: "— 容错设计原则" },
            { quote: "拓扑隔离：一个模块的错误不应扩散到另一个。", author: "— 容错架构原则" },
            { quote: "异步解耦：不同步，就不阻塞。", author: "— 架构设计原则" },
            { quote: "Observability 不是监控，是回答任意问题的能力。", author: "— 可观测性格言" },
            { quote: "日志要能回答事发时发生了什么，而不是事发后你希望知道什么。", author: "— 运维格言" },
            { quote: "优雅降级胜过粗暴报错。", author: "— 用户体验原则" },
            { quote: "一致性是用户能感知的正确性，不是系统的内部协议。", author: "— 用户体验原则" },
            { quote: "渐近式增强：从可用到好用，一步步来。", author: "— Web 设计原则" },
            { quote: "响应式设计不是适配屏幕，是适配场景。", author: "— 前端设计原则" },
            { quote: "无障碍不是功能，是权利。", author: "— Web 可访问性原则" },
            { quote: "安全是设计出来的，不是测试出来的。", author: "— 安全设计原则" },
            { quote: "最小权限原则：只给刚刚好的权限。", author: "— 安全架构原则" },
            { quote: "防御深度：没有单一道防线是可靠的。", author: "— 安全架构原则" },
            { quote: "不信任用户输入，不信任上游服务，不信任自己。", author: "— 零信任格言" },
            { quote: "加密不是可选项，是默认项。", author: "— 安全默认原则" },
            { quote: "schema 是契约，API 是承诺。", author: "— API 设计原则" },
            { quote: "向后兼容不是美德，是底线。", author: "— API 版本化原则" },
            { quote: "POST 不是 GET 的安全替代品。", author: "— REST 设计原则" },
            { quote: "优雅的 API 让调用者感到被尊重。", author: "— API 设计美学" },
            { quote: "数据库设计先于代码设计，模式先于逻辑。", author: "— 数据建模原则" },
            { quote: "索引不是银弹，全表扫描也不是魔鬼。", author: "— 数据库优化格言" },
            { quote: "范式化减少冗余，反范式化提升性能，权衡是艺术。", author: "— 数据库设计原则" },
            { quote: "事务是边界，不是万能药。", author: "— 数据库事务格言" },
            { quote: "选择合适的数据结构，比优化算法更有用。", author: "— 数据结构格言" },
            { quote: "抽象泄漏定律：所有非平凡的抽象都有泄漏。", author: "— Joel Spolsky" },
            { quote: "技术债务像经济债务：少量能加速，多了会破产。", author: "— Ward Cunningham" },
            { quote: "好的文档让人不需要问问题。", author: "— 文档原则" },
            { quote: "Code review 是知识的传递，不是找茬。", author: "— 团队协作格言" },
            { quote: "Pair programming 的键盘只有一把，但脑子有两颗。", author: "— 极限编程格言" },
            { quote: "自动化一切可以自动化的事情。", author: "— DevOps 格言" },
            { quote: "基础设施即代码，环境即版本。", author: "— 运维自动化原则" },
            { quote: "用数据说话，用指标决策。", author: "— 数据驱动格言" },
            { quote: "Commit 小，频繁，可回溯。", author: "— Git 提交原则" },
            { quote: "分支策略是协作的契约，不是约束。", author: "— Git 分支原则" },
            { quote: "CI 通过是 merge 的门票，不是选配。", author: "— CI/CD 原则" },
            { quote: "测试环境应尽可能接近生产环境。", author: "— 环境管理原则" },
            { quote: "Premature abstraction is the root of all evil.", author: "— 软件工程箴言" },
            { quote: "A complex system that works is invariably found to have evolved from a simple system that worked.", author: "— John Gall" },
            { quote: "Perfection is achieved not when there is nothing more to add, but when there is nothing left to take away.", author: "— Antoine de Saint-Exupery" },
            { quote: "Always design things by considering them in their larger context.", author: "— Eliel Saarinen" },
            { quote: "The purpose of abstraction is not to be vague, but to create a new semantic level.", author: "— Edsger W. Dijkstra" },
            { quote: "细节不会决定成败，但架构会。", author: "— 架构箴言" },
            { quote: "好的架构是妥协的艺术：在约束中寻找最优解。", author: "— bytedepth" },
            { quote: "一个系统你不敢改的，就是坏架构。", author: "— 架构评估格言" }
        ];
        var lastIdx = -1;
        function randomIdx() {
            var idx;
            do { idx = Math.floor(Math.random() * slogans.length); } while (idx === lastIdx);
            lastIdx = idx;
            return idx;
        }
        function showSlogan(idx) {
            var el = document.getElementById('legacy-slogan-display');
            el.classList.remove('active');
            setTimeout(function() {
                document.getElementById('legacy-slogan-quote').textContent = slogans[idx].quote;
                document.getElementById('legacy-slogan-author').textContent = slogans[idx].author;
                el.classList.add('active');
            }, 600);
        }
        var idx = randomIdx();
        document.getElementById('legacy-slogan-quote').textContent = slogans[idx].quote;
        document.getElementById('legacy-slogan-author').textContent = slogans[idx].author;
        document.getElementById('legacy-slogan-display').classList.add('active');
        setInterval(function() { showSlogan(randomIdx()); }, 6000);
    })();
