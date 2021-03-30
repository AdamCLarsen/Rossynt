﻿using System;
using JetBrains.Annotations;

namespace RoslynSyntaxTreeBackend.Models {
    public sealed class ProjectRecord {
        [NotNull] public string ProjectId { get; }
        [NotNull] public Tree Tree { get; }

        // ******************************************************************************** //

        public ProjectRecord([NotNull] string projectId, [NotNull] Tree tree) {
            ProjectId = projectId ?? throw new ArgumentNullException(nameof(projectId));
            Tree = tree ?? throw new ArgumentNullException(nameof(tree));
        }
    }
}