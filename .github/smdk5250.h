<!DOCTYPE html>
<html class="" lang="en">
<head prefix="og: http://ogp.me/ns#">
<meta charset="utf-8">
<meta content="IE=edge" http-equiv="X-UA-Compatible">
<meta content="object" property="og:type">
<meta content="GitLab" property="og:site_name">
<meta content="include/configs/smdk5250.h · HEAD · U-Boot / U-Boot" property="og:title">
<meta content="&quot;Das U-Boot&quot; Source Tree" property="og:description">
<meta content="/uploads/-/system/project/avatar/531/u-boot_logo.bmp" property="og:image">
<meta content="64" property="og:image:width">
<meta content="64" property="og:image:height">
<meta content="https://gitlab.denx.de/u-boot/u-boot/blob/HEAD/include/configs/smdk5250.h" property="og:url">
<meta content="summary" property="twitter:card">
<meta content="include/configs/smdk5250.h · HEAD · U-Boot / U-Boot" property="twitter:title">
<meta content="&quot;Das U-Boot&quot; Source Tree" property="twitter:description">
<meta content="/uploads/-/system/project/avatar/531/u-boot_logo.bmp" property="twitter:image">

<title>include/configs/smdk5250.h · HEAD · U-Boot / U-Boot · GitLab</title>
<meta content="&quot;Das U-Boot&quot; Source Tree" name="description">
<link rel="shortcut icon" type="image/png" href="/assets/favicon-7901bd695fb93edb07975966062049829afb56cf11511236e61bcf425070e36e.png" id="favicon" data-original-href="/assets/favicon-7901bd695fb93edb07975966062049829afb56cf11511236e61bcf425070e36e.png" />
<link rel="stylesheet" media="all" href="/assets/application-def1880ada798c68ee010ba2193f53a2c65a8981871a634ae7e18ccdcd503fa3.css" />
<link rel="stylesheet" media="print" href="/assets/print-74c3df10dad473d66660c828e3aa54ca3bfeac6d8bb708643331403fe7211e60.css" />


<link rel="stylesheet" media="all" href="/assets/highlight/themes/white-3144068cf4f603d290f553b653926358ddcd02493b9728f62417682657fc58c0.css" />
<script>
//<![CDATA[
window.gon={};gon.api_version="v4";gon.default_avatar_url="https://gitlab.denx.de/assets/no_avatar-849f9c04a3a0d0cea2424ae97b27447dc64a7dbfae83c036c45b403392f0e8ba.png";gon.max_file_size=10;gon.asset_host=null;gon.webpack_public_path="/assets/webpack/";gon.relative_url_root="";gon.shortcuts_path="/help/shortcuts";gon.user_color_scheme="white";gon.gitlab_url="https://gitlab.denx.de";gon.revision="2417d5becc7";gon.gitlab_logo="/assets/gitlab_logo-7ae504fe4f68fdebb3c2034e36621930cd36ea87924c11ff65dbcb8ed50dca58.png";gon.sprite_icons="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg";gon.sprite_file_icons="/assets/file_icons-7262fc6897e02f1ceaf8de43dc33afa5e4f9a2067f4f68ef77dcc87946575e9e.svg";gon.emoji_sprites_css_path="/assets/emoji_sprites-289eccffb1183c188b630297431be837765d9ff4aed6130cf738586fb307c170.css";gon.test_env=false;gon.suggested_label_colors={"#0033CC":"UA blue","#428BCA":"Moderate blue","#44AD8E":"Lime green","#A8D695":"Feijoa","#5CB85C":"Slightly desaturated green","#69D100":"Bright green","#004E00":"Very dark lime green","#34495E":"Very dark desaturated blue","#7F8C8D":"Dark grayish cyan","#A295D6":"Slightly desaturated blue","#5843AD":"Dark moderate blue","#8E44AD":"Dark moderate violet","#FFECDB":"Very pale orange","#AD4363":"Dark moderate pink","#D10069":"Strong pink","#CC0033":"Strong red","#FF0000":"Pure red","#D9534F":"Soft red","#D1D100":"Strong yellow","#F0AD4E":"Soft orange","#AD8D43":"Dark moderate orange"};gon.first_day_of_week=1;gon.ee=false;
//]]>
</script>


<script src="/assets/webpack/runtime.15f5b13a.bundle.js" defer="defer"></script>
<script src="/assets/webpack/main.f8ac0dda.chunk.js" defer="defer"></script>
<script src="/assets/webpack/commons~pages.admin.clusters~pages.admin.clusters.destroy~pages.admin.clusters.edit~pages.admin.clus~17e57b7f.1e0b3713.chunk.js" defer="defer"></script>
<script src="/assets/webpack/commons~pages.groups.milestones.edit~pages.groups.milestones.new~pages.projects.blame.show~pages.pro~d3e579ac.0dc404e7.chunk.js" defer="defer"></script>
<script src="/assets/webpack/pages.projects.blob.show.d9236e3f.chunk.js" defer="defer"></script>

<meta name="csrf-param" content="authenticity_token" />
<meta name="csrf-token" content="aAO0tq7oVI5yIxjc20pxLxZ36kBUq0+q+sw1JN6ZwE8onv11FSGDZGdErD31UYcrF5k+bNKn7PiASvng21HXpw==" />

<meta content="origin-when-cross-origin" name="referrer">
<meta content="width=device-width, initial-scale=1, maximum-scale=1" name="viewport">
<meta content="#474D57" name="theme-color">
<link rel="apple-touch-icon" type="image/x-icon" href="/assets/touch-icon-iphone-5a9cee0e8a51212e70b90c87c12f382c428870c0ff67d1eb034d884b78d2dae7.png" />
<link rel="apple-touch-icon" type="image/x-icon" href="/assets/touch-icon-ipad-a6eec6aeb9da138e507593b464fdac213047e49d3093fc30e90d9a995df83ba3.png" sizes="76x76" />
<link rel="apple-touch-icon" type="image/x-icon" href="/assets/touch-icon-iphone-retina-72e2aadf86513a56e050e7f0f2355deaa19cc17ed97bbe5147847f2748e5a3e3.png" sizes="120x120" />
<link rel="apple-touch-icon" type="image/x-icon" href="/assets/touch-icon-ipad-retina-8ebe416f5313483d9c1bc772b5bbe03ecad52a54eba443e5215a22caed2a16a2.png" sizes="152x152" />
<link color="rgb(226, 67, 41)" href="/assets/logo-d36b5212042cebc89b96df4bf6ac24e43db316143e89926c0db839ff694d2de4.svg" rel="mask-icon">
<meta content="/assets/msapplication-tile-1196ec67452f618d39cdd85e2e3a542f76574c071051ae7effbfde01710eb17d.png" name="msapplication-TileImage">
<meta content="#30353E" name="msapplication-TileColor">




</head>

<body class="ui-indigo  gl-browser-chrome gl-platform-chrome_os" data-find-file="/u-boot/u-boot/find_file/HEAD" data-group="" data-page="projects:blob:show" data-project="u-boot">

<script>
//<![CDATA[
gl = window.gl || {};
gl.client = {"isChrome":true,"isChrome Os":true};


//]]>
</script>


<header class="navbar navbar-gitlab navbar-expand-sm js-navbar" data-qa-selector="navbar">
<a class="sr-only gl-accessibility" href="#content-body" tabindex="1">Skip to content</a>
<div class="container-fluid">
<div class="header-content">
<div class="title-container">
<h1 class="title">
<a title="Dashboard" id="logo" href="/"><svg width="24" height="24" class="tanuki-logo" viewBox="0 0 36 36">
  <path class="tanuki-shape tanuki-left-ear" fill="#e24329" d="M2 14l9.38 9v-9l-4-12.28c-.205-.632-1.176-.632-1.38 0z"/>
  <path class="tanuki-shape tanuki-right-ear" fill="#e24329" d="M34 14l-9.38 9v-9l4-12.28c.205-.632 1.176-.632 1.38 0z"/>
  <path class="tanuki-shape tanuki-nose" fill="#e24329" d="M18,34.38 3,14 33,14 Z"/>
  <path class="tanuki-shape tanuki-left-eye" fill="#fc6d26" d="M18,34.38 11.38,14 2,14 6,25Z"/>
  <path class="tanuki-shape tanuki-right-eye" fill="#fc6d26" d="M18,34.38 24.62,14 34,14 30,25Z"/>
  <path class="tanuki-shape tanuki-left-cheek" fill="#fca326" d="M2 14L.1 20.16c-.18.565 0 1.2.5 1.56l17.42 12.66z"/>
  <path class="tanuki-shape tanuki-right-cheek" fill="#fca326" d="M34 14l1.9 6.16c.18.565 0 1.2-.5 1.56L18 34.38z"/>
</svg>

<span class="logo-text d-none d-lg-block prepend-left-8">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 617 169"><path d="M315.26 2.97h-21.8l.1 162.5h88.3v-20.1h-66.5l-.1-142.4M465.89 136.95c-5.5 5.7-14.6 11.4-27 11.4-16.6 0-23.3-8.2-23.3-18.9 0-16.1 11.2-23.8 35-23.8 4.5 0 11.7.5 15.4 1.2v30.1h-.1m-22.6-98.5c-17.6 0-33.8 6.2-46.4 16.7l7.7 13.4c8.9-5.2 19.8-10.4 35.5-10.4 17.9 0 25.8 9.2 25.8 24.6v7.9c-3.5-.7-10.7-1.2-15.1-1.2-38.2 0-57.6 13.4-57.6 41.4 0 25.1 15.4 37.7 38.7 37.7 15.7 0 30.8-7.2 36-18.9l4 15.9h15.4v-83.2c-.1-26.3-11.5-43.9-44-43.9M557.63 149.1c-8.2 0-15.4-1-20.8-3.5V70.5c7.4-6.2 16.6-10.7 28.3-10.7 21.1 0 29.2 14.9 29.2 39 0 34.2-13.1 50.3-36.7 50.3m9.2-110.6c-19.5 0-30 13.3-30 13.3v-21l-.1-27.8h-21.3l.1 158.5c10.7 4.5 25.3 6.9 41.2 6.9 40.7 0 60.3-26 60.3-70.9-.1-35.5-18.2-59-50.2-59M77.9 20.6c19.3 0 31.8 6.4 39.9 12.9l9.4-16.3C114.5 6 97.3 0 78.9 0 32.5 0 0 28.3 0 85.4c0 59.8 35.1 83.1 75.2 83.1 20.1 0 37.2-4.7 48.4-9.4l-.5-63.9V75.1H63.6v20.1h38l.5 48.5c-5 2.5-13.6 4.5-25.3 4.5-32.2 0-53.8-20.3-53.8-63-.1-43.5 22.2-64.6 54.9-64.6M231.43 2.95h-21.3l.1 27.3v94.3c0 26.3 11.4 43.9 43.9 43.9 4.5 0 8.9-.4 13.1-1.2v-19.1c-3.1.5-6.4.7-9.9.7-17.9 0-25.8-9.2-25.8-24.6v-65h35.7v-17.8h-35.7l-.1-38.5M155.96 165.47h21.3v-124h-21.3v124M155.96 24.37h21.3V3.07h-21.3v21.3"/></svg>

</span>
</a></h1>
<ul class="list-unstyled navbar-sub-nav">
<li class="home"><a title="Projects" class="dashboard-shortcuts-projects" href="/explore">Projects
</a></li><li class=""><a title="Groups" class="dashboard-shortcuts-groups" href="/explore/groups">Groups
</a></li><li class=""><a title="Snippets" class="dashboard-shortcuts-snippets" href="/explore/snippets">Snippets
</a></li><li>
<a title="About GitLab CE" href="/help">Help</a>
</li>
</ul>

</div>
<div class="navbar-collapse collapse">
<ul class="nav navbar-nav">
<li class="nav-item d-none d-sm-none d-md-block m-auto">
<div class="search search-form" data-track-event="activate_form_input" data-track-label="navbar_search" data-track-value="">
<form class="form-inline" action="/search" accept-charset="UTF-8" method="get"><input name="utf8" type="hidden" value="&#x2713;" /><div class="search-input-container">
<div class="search-input-wrap">
<div class="dropdown" data-url="/search/autocomplete">
<input type="search" name="search" id="search" placeholder="Search or jump to…" class="search-input dropdown-menu-toggle no-outline js-search-dashboard-options" spellcheck="false" tabindex="1" autocomplete="off" data-issues-path="/dashboard/issues" data-mr-path="/dashboard/merge_requests" data-qa-selector="search_term_field" aria-label="Search or jump to…" />
<button class="hidden js-dropdown-search-toggle" data-toggle="dropdown" type="button"></button>
<div class="dropdown-menu dropdown-select js-dashboard-search-options">
<div class="dropdown-content"><ul>
<li class="dropdown-menu-empty-item">
<a>
Loading...
</a>
</li>
</ul>
</div><div class="dropdown-loading"><i aria-hidden="true" data-hidden="true" class="fa fa-spinner fa-spin"></i></div>
</div>
<svg class="s16 search-icon"><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#search"></use></svg>
<svg class="s16 clear-icon js-clear-input"><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#close"></use></svg>
</div>
</div>
</div>
<input type="hidden" name="group_id" id="group_id" class="js-search-group-options" />
<input type="hidden" name="project_id" id="search_project_id" value="531" class="js-search-project-options" data-project-path="u-boot" data-name="U-Boot" data-issues-path="/u-boot/u-boot/issues" data-mr-path="/u-boot/u-boot/merge_requests" data-issues-disabled="true" />
<input type="hidden" name="search_code" id="search_code" value="true" />
<input type="hidden" name="repository_ref" id="repository_ref" value="HEAD" />
<input type="hidden" name="nav_source" id="nav_source" value="navbar" />
<div class="search-autocomplete-opts hide" data-autocomplete-path="/search/autocomplete" data-autocomplete-project-id="531" data-autocomplete-project-ref="HEAD"></div>
</form></div>

</li>
<li class="nav-item d-inline-block d-sm-none d-md-none">
<a title="Search" aria-label="Search" data-toggle="tooltip" data-placement="bottom" data-container="body" href="/search?project_id=531"><svg class="s16"><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#search"></use></svg>
</a></li>
<li class="nav-item header-help dropdown">
<a class="header-help-dropdown-toggle" data-toggle="dropdown" href="/help"><svg class="s16"><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#question"></use></svg>
<svg class="caret-down"><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#angle-down"></use></svg>
</a><div class="dropdown-menu dropdown-menu-right">
<ul>
<li>
<a href="/help">Help</a>
</li>
<li>
<a href="https://about.gitlab.com/getting-help/">Support</a>
</li>

<li class="divider"></li>
<li>
<a href="https://about.gitlab.com/submit-feedback">Submit feedback</a>
</li>
<li>
<a target="_blank" class="text-nowrap" href="https://about.gitlab.com/contributing">Contribute to GitLab
</a></li>


</ul>

</div>
</li>
<li class="nav-item">
<div>
<a class="btn btn-sign-in" href="/users/sign_in?redirect_to_referer=yes">Sign in</a>
</div>
</li>
</ul>
</div>
<button class="navbar-toggler d-block d-sm-none" type="button">
<span class="sr-only">Toggle navigation</span>
<svg class="s12 more-icon js-navbar-toggle-right"><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#ellipsis_h"></use></svg>
<svg class="s12 close-icon js-navbar-toggle-left"><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#close"></use></svg>
</button>
</div>
</div>
</header>

<div class="layout-page page-with-contextual-sidebar">
<div class="nav-sidebar">
<div class="nav-sidebar-inner-scroll">
<div class="context-header">
<a title="U-Boot" href="/u-boot/u-boot"><div class="avatar-container rect-avatar s40 project-avatar">
<img alt="U-Boot" class="avatar s40 avatar-tile lazy" width="40" height="40" data-src="/uploads/-/system/project/avatar/531/u-boot_logo.bmp" src="data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==" />
</div>
<div class="sidebar-context-title">
U-Boot
</div>
</a></div>
<ul class="sidebar-top-level-items">
<li class="home"><a class="shortcuts-project rspec-project-link" data-qa-selector="project_link" href="/u-boot/u-boot"><div class="nav-icon-container">
<svg><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#home"></use></svg>
</div>
<span class="nav-item-name">
Project
</span>
</a><ul class="sidebar-sub-level-items">
<li class="fly-out-top-item"><a href="/u-boot/u-boot"><strong class="fly-out-top-item-name">
Project
</strong>
</a></li><li class="divider fly-out-top-item"></li>
<li class=""><a title="Project details" class="shortcuts-project" href="/u-boot/u-boot"><span>Details</span>
</a></li><li class=""><a title="Activity" class="shortcuts-project-activity" data-qa-selector="activity_link" href="/u-boot/u-boot/activity"><span>Activity</span>
</a></li><li class=""><a title="Releases" class="shortcuts-project-releases" href="/u-boot/u-boot/-/releases"><span>Releases</span>
</a></li><li class=""><a title="Cycle Analytics" class="shortcuts-project-cycle-analytics" href="/u-boot/u-boot/cycle_analytics"><span>Cycle Analytics</span>
</a></li>
</ul>
</li><li class="active"><a class="shortcuts-tree qa-project-menu-repo" href="/u-boot/u-boot/tree/HEAD"><div class="nav-icon-container">
<svg><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#doc-text"></use></svg>
</div>
<span class="nav-item-name" id="js-onboarding-repo-link">
Repository
</span>
</a><ul class="sidebar-sub-level-items">
<li class="fly-out-top-item active"><a href="/u-boot/u-boot/tree/HEAD"><strong class="fly-out-top-item-name">
Repository
</strong>
</a></li><li class="divider fly-out-top-item"></li>
<li class="active"><a href="/u-boot/u-boot/tree/HEAD">Files
</a></li><li class=""><a id="js-onboarding-commits-link" href="/u-boot/u-boot/commits/HEAD">Commits
</a></li><li class=""><a class="qa-branches-link" id="js-onboarding-branches-link" href="/u-boot/u-boot/-/branches">Branches
</a></li><li class=""><a href="/u-boot/u-boot/-/tags">Tags
</a></li><li class=""><a href="/u-boot/u-boot/-/graphs/HEAD">Contributors
</a></li><li class=""><a href="/u-boot/u-boot/-/network/HEAD">Graph
</a></li><li class=""><a href="/u-boot/u-boot/compare?from=master&amp;to=HEAD">Compare
</a></li><li class=""><a href="/u-boot/u-boot/-/graphs/HEAD/charts">Charts
</a></li>
</ul>
</li><li class=""><a class="shortcuts-pipelines qa-link-pipelines rspec-link-pipelines" href="/u-boot/u-boot/pipelines"><div class="nav-icon-container">
<svg><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#rocket"></use></svg>
</div>
<span class="nav-item-name" id="js-onboarding-pipelines-link">
CI / CD
</span>
</a><ul class="sidebar-sub-level-items">
<li class="fly-out-top-item"><a href="/u-boot/u-boot/pipelines"><strong class="fly-out-top-item-name">
CI / CD
</strong>
</a></li><li class="divider fly-out-top-item"></li>
<li class=""><a title="Pipelines" class="shortcuts-pipelines" href="/u-boot/u-boot/pipelines"><span>
Pipelines
</span>
</a></li><li class=""><a title="Jobs" class="shortcuts-builds" href="/u-boot/u-boot/-/jobs"><span>
Jobs
</span>
</a></li><li class=""><a title="Schedules" class="shortcuts-builds" href="/u-boot/u-boot/pipeline_schedules"><span>
Schedules
</span>
</a></li><li class=""><a title="Charts" class="shortcuts-pipelines-charts" href="/u-boot/u-boot/pipelines/charts"><span>
Charts
</span>
</a></li></ul>
</li>

<li class=""><a title="Members" class="shortcuts-tree" href="/u-boot/u-boot/-/settings/members"><div class="nav-icon-container">
<svg><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#users"></use></svg>
</div>
<span class="nav-item-name">
Members
</span>
</a><ul class="sidebar-sub-level-items is-fly-out-only">
<li class="fly-out-top-item"><a href="/u-boot/u-boot/-/project_members"><strong class="fly-out-top-item-name">
Members
</strong>
</a></li></ul>
</li><a class="toggle-sidebar-button js-toggle-sidebar qa-toggle-sidebar rspec-toggle-sidebar" role="button" title="Toggle sidebar" type="button">
<svg class="icon-angle-double-left"><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#angle-double-left"></use></svg>
<svg class="icon-angle-double-right"><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#angle-double-right"></use></svg>
<span class="collapse-text">Collapse sidebar</span>
</a>
<button name="button" type="button" class="close-nav-button"><svg class="s16"><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#close"></use></svg>
<span class="collapse-text">Close sidebar</span>
</button>
<li class="hidden">
<a title="Activity" class="shortcuts-project-activity" href="/u-boot/u-boot/activity"><span>
Activity
</span>
</a></li>
<li class="hidden">
<a title="Network" class="shortcuts-network" href="/u-boot/u-boot/-/network/HEAD">Graph
</a></li>
<li class="hidden">
<a title="Charts" class="shortcuts-repository-charts" href="/u-boot/u-boot/-/graphs/HEAD/charts">Charts
</a></li>
<li class="hidden">
<a title="Jobs" class="shortcuts-builds" href="/u-boot/u-boot/-/jobs">Jobs
</a></li>
<li class="hidden">
<a title="Commits" class="shortcuts-commits" href="/u-boot/u-boot/commits/HEAD">Commits
</a></li>
</ul>
</div>
</div>

<div class="content-wrapper">

<div class="mobile-overlay"></div>
<div class="alert-wrapper">






<nav class="breadcrumbs container-fluid container-limited" role="navigation">
<div class="breadcrumbs-container">
<button name="button" type="button" class="toggle-mobile-nav"><span class="sr-only">Open sidebar</span>
<i aria-hidden="true" data-hidden="true" class="fa fa-bars"></i>
</button><div class="breadcrumbs-links js-title-container">
<ul class="list-unstyled breadcrumbs-list js-breadcrumbs-list">
<li><a class="group-path breadcrumb-item-text js-breadcrumb-item-text " href="/u-boot"><img class="avatar-tile lazy" width="15" height="15" data-src="/uploads/-/system/group/avatar/324/u-boot_logo.bmp" src="data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==" />U-Boot</a><svg class="s8 breadcrumbs-list-angle"><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#angle-right"></use></svg></li> <li><a href="/u-boot/u-boot"><img alt="U-Boot" class="avatar-tile lazy" width="15" height="15" data-src="/uploads/-/system/project/avatar/531/u-boot_logo.bmp" src="data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==" /><span class="breadcrumb-item-text js-breadcrumb-item-text">U-Boot</span></a><svg class="s8 breadcrumbs-list-angle"><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#angle-right"></use></svg></li>

<li>
<h2 class="breadcrumbs-sub-title"><a href="/u-boot/u-boot/blob/HEAD/include/configs/smdk5250.h">Repository</a></h2>
</li>
</ul>
</div>

</div>
</nav>

<div class="d-flex"></div>
</div>
<div class="container-fluid container-limited ">
<div class="content" id="content-body">
<div class="flash-container flash-container-page sticky">
</div>

<div class="js-signature-container" data-signatures-path="/u-boot/u-boot/commits/83d290c56fab2d38cd1ab4c4cc7099559c1d5046/signatures"></div>

<div class="tree-holder" id="tree-holder">
<div class="nav-block">
<div class="tree-ref-container">
<div class="tree-ref-holder">
<form class="project-refs-form" action="/u-boot/u-boot/refs/switch" accept-charset="UTF-8" method="get"><input name="utf8" type="hidden" value="&#x2713;" /><input type="hidden" name="destination" id="destination" value="blob" />
<input type="hidden" name="path" id="path" value="include/configs/smdk5250.h" />
<div class="dropdown">
<button class="dropdown-menu-toggle js-project-refs-dropdown qa-branches-select" type="button" data-toggle="dropdown" data-selected="HEAD" data-ref="HEAD" data-refs-url="/u-boot/u-boot/refs?sort=updated_desc" data-field-name="ref" data-submit-form-on-click="true" data-visit="true"><span class="dropdown-toggle-text ">HEAD</span><i aria-hidden="true" data-hidden="true" class="fa fa-chevron-down"></i></button>
<div class="dropdown-menu dropdown-menu-paging dropdown-menu-selectable git-revision-dropdown qa-branches-dropdown">
<div class="dropdown-page-one">
<div class="dropdown-title"><span>Switch branch/tag</span><button class="dropdown-title-button dropdown-menu-close" aria-label="Close" type="button"><i aria-hidden="true" data-hidden="true" class="fa fa-times dropdown-menu-close-icon"></i></button></div>
<div class="dropdown-input"><input type="search" id="" class="dropdown-input-field qa-dropdown-input-field" placeholder="Search branches and tags" autocomplete="off" /><i aria-hidden="true" data-hidden="true" class="fa fa-search dropdown-input-search"></i><i aria-hidden="true" data-hidden="true" role="button" class="fa fa-times dropdown-input-clear js-dropdown-input-clear"></i></div>
<div class="dropdown-content"></div>
<div class="dropdown-loading"><i aria-hidden="true" data-hidden="true" class="fa fa-spinner fa-spin"></i></div>
</div>
</div>
</div>
</form>
</div>
<ul class="breadcrumb repo-breadcrumb">
<li class="breadcrumb-item">
<a href="/u-boot/u-boot/tree/HEAD">u-boot
</a></li>
<li class="breadcrumb-item">
<a href="/u-boot/u-boot/tree/HEAD/include">include</a>
</li>
<li class="breadcrumb-item">
<a href="/u-boot/u-boot/tree/HEAD/include/configs">configs</a>
</li>
<li class="breadcrumb-item">
<a href="/u-boot/u-boot/blob/HEAD/include/configs/smdk5250.h"><strong>smdk5250.h</strong>
</a></li>
</ul>
</div>
<div class="tree-controls">
<a class="btn shortcuts-find-file" rel="nofollow" href="/u-boot/u-boot/find_file/HEAD"><i aria-hidden="true" data-hidden="true" class="fa fa-search"></i>
<span>Find file</span>
</a>
<div class="btn-group" role="group"><a class="btn js-blob-blame-link" href="/u-boot/u-boot/blame/HEAD/include/configs/smdk5250.h">Blame</a><a class="btn" href="/u-boot/u-boot/commits/HEAD/include/configs/smdk5250.h">History</a><a class="btn js-data-file-blob-permalink-url" href="/u-boot/u-boot/blob/548aefa5b9e5c31681e0a8bd78e96b66eedd1137/include/configs/smdk5250.h">Permalink</a></div>
</div>
</div>

<div class="info-well d-none d-sm-block">
<div class="well-segment">
<ul class="blob-commit-info">
<li class="commit flex-row js-toggle-container" id="commit-83d290c5">
<div class="avatar-cell d-none d-sm-block">
<a href="/trini"><img alt="Tom Rini&#39;s avatar" src="https://secure.gravatar.com/avatar/ecc915c3fdee5b44baf6d5951918cbd1?s=80&amp;d=identicon" class="avatar s40 d-none d-sm-inline-block" title="Tom Rini" /></a>
</div>
<div class="commit-detail flex-list">
<div class="commit-content qa-commit-content">
<a class="commit-row-message item-title js-onboarding-commit-item" href="/u-boot/u-boot/commit/83d290c56fab2d38cd1ab4c4cc7099559c1d5046">SPDX: Convert all of our single license tags to Linux Kernel style</a>
<span class="commit-row-message d-inline d-sm-none">
&middot;
83d290c5
</span>
<button class="text-expander js-toggle-button">
<svg class="s12"><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#ellipsis_h"></use></svg>
</button>
<div class="committer">
<a class="commit-author-link js-user-link" data-user-id="255" href="/trini">Tom Rini</a> authored <time class="js-timeago" title="May 6, 2018 9:58pm" datetime="2018-05-06T21:58:06Z" data-toggle="tooltip" data-placement="bottom" data-container="body">May 06, 2018</time>
</div>

<pre class="commit-row-description js-toggle-content append-bottom-8">&#x000A;When U-Boot started using SPDX tags we were among the early adopters and&#x000A;there weren't a lot of other examples to borrow from.  So we picked the&#x000A;area of the file that usually had a full license text and replaced it&#x000A;with an appropriate SPDX-License-Identifier: entry.  Since then, the&#x000A;Linux Kernel has adopted SPDX tags and they place it as the very first&#x000A;line in a file (except where shebangs are used, then it's second line)&#x000A;and with slightly different comment styles than us.&#x000A;&#x000A;In part due to community overlap, in part due to better tag visibility&#x000A;and in part for other minor reasons, switch over to that style.&#x000A;&#x000A;This commit changes all instances where we have a single declared&#x000A;license in the tag as both the before and after are identical in tag&#x000A;contents.  There's also a few places where I found we did not have a tag&#x000A;and have introduced one.&#x000A;Signed-off-by: <span data-trailer="Signed-off-by:" data-user="255"><a href="https://gitlab.denx.de/trini" title="trini@konsulko.com"><img alt="Tom Rini's avatar" src="https://secure.gravatar.com/avatar/ecc915c3fdee5b44baf6d5951918cbd1?s=32&amp;d=identicon" class="avatar s16 avatar-inline" title="Tom Rini"></a><a href="https://gitlab.denx.de/trini" title="trini@konsulko.com">Tom Rini</a> &lt;<a href="mailto:trini@konsulko.com" title="trini@konsulko.com">trini@konsulko.com</a>&gt;</span></pre>
</div>
<div class="commit-actions flex-row">

<div class="js-commit-pipeline-status" data-endpoint="/u-boot/u-boot/commit/83d290c56fab2d38cd1ab4c4cc7099559c1d5046/pipelines?ref=HEAD"></div>
<div class="commit-sha-group d-none d-sm-flex">
<div class="label label-monospace monospace">
83d290c5
</div>
<button class="btn btn btn-default" data-toggle="tooltip" data-placement="bottom" data-container="body" data-title="Copy commit SHA to clipboard" data-class="btn btn-default" data-clipboard-text="83d290c56fab2d38cd1ab4c4cc7099559c1d5046" type="button" title="Copy commit SHA to clipboard" aria-label="Copy commit SHA to clipboard"><svg><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#duplicate"></use></svg></button>

</div>
</div>
</div>
</li>

</ul>
</div>


</div>
<div class="blob-content-holder" id="blob-content-holder">
<article class="file-holder">
<div class="js-file-title file-title-flex-parent">
<div class="file-header-content">
<i aria-hidden="true" data-hidden="true" class="fa fa-file-text-o fa-fw"></i>
<strong class="file-title-name qa-file-title-name">
smdk5250.h
</strong>
<button class="btn btn-clipboard btn-transparent" data-toggle="tooltip" data-placement="bottom" data-container="body" data-class="btn-clipboard btn-transparent" data-title="Copy file path to clipboard" data-clipboard-text="{&quot;text&quot;:&quot;include/configs/smdk5250.h&quot;,&quot;gfm&quot;:&quot;`include/configs/smdk5250.h`&quot;}" type="button" title="Copy file path to clipboard" aria-label="Copy file path to clipboard"><svg><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#duplicate"></use></svg></button>
<small class="mr-1">
507 Bytes
</small>
</div>

<div class="file-actions">

<div class="btn-group" role="group"><button class="btn btn btn-sm js-copy-blob-source-btn" data-toggle="tooltip" data-placement="bottom" data-container="body" data-class="btn btn-sm js-copy-blob-source-btn" data-title="Copy source to clipboard" data-clipboard-target=".blob-content[data-blob-id=&#39;82251b36150e595e76b89e9c8a6f3fdd357ccda9&#39;]" type="button" title="Copy source to clipboard" aria-label="Copy source to clipboard"><svg><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#duplicate"></use></svg></button><a class="btn btn-sm has-tooltip" target="_blank" rel="noopener noreferrer" title="Open raw" data-container="body" href="/u-boot/u-boot/raw/HEAD/include/configs/smdk5250.h"><i aria-hidden="true" data-hidden="true" class="fa fa-file-code-o"></i></a><a download="include/configs/smdk5250.h" class="btn btn-sm has-tooltip" target="_blank" rel="noopener noreferrer" title="Download" data-container="body" href="/u-boot/u-boot/raw/HEAD/include/configs/smdk5250.h?inline=false"><svg><use xlink:href="/assets/icons-4009ebf96719f129f954d643e65f87152b5e6c4a4917130c4a696beb54af9949.svg#download"></use></svg></a></div>
<div class="btn-group" role="group"><button name="button" type="submit" class="btn js-edit-blob  disabled has-tooltip" title="You can only edit files when you are on a branch" data-container="body">Edit</button><button name="button" type="submit" class="btn btn-default disabled has-tooltip" title="You can only edit files when you are on a branch" data-container="body">Web IDE</button></div>
</div>
</div>



<div class="blob-viewer" data-type="simple" data-url="/u-boot/u-boot/blob/HEAD/include/configs/smdk5250.h?format=json&amp;viewer=simple">
<div class="text-center prepend-top-default append-bottom-default">
<i aria-hidden="true" aria-label="Loading content…" class="fa fa-spinner fa-spin fa-2x qa-spinner"></i>
</div>

</div>


</article>
</div>

<div class="modal" id="modal-upload-blob">
<div class="modal-dialog modal-lg">
<div class="modal-content">
<div class="modal-header">
<h3 class="page-title">Replace smdk5250.h</h3>
<button aria-label="Close" class="close" data-dismiss="modal" type="button">
<span aria-hidden="true">&times;</span>
</button>
</div>
<div class="modal-body">
<form class="js-quick-submit js-upload-blob-form" data-method="put" action="/u-boot/u-boot/update/HEAD/include/configs/smdk5250.h" accept-charset="UTF-8" method="post"><input name="utf8" type="hidden" value="&#x2713;" /><input type="hidden" name="_method" value="put" /><input type="hidden" name="authenticity_token" value="WtOWQVlXE+0sPs69Lx9nwa2Er6wiw0PBaKs3rVzGRI0aTt+C4p7EBzlZelwBBJHFrGp7gKTP4JMSLftpWQ5TZQ==" /><div class="dropzone">
<div class="dropzone-previews blob-upload-dropzone-previews">
<p class="dz-message light">
Attach a file by drag &amp; drop or <a class="markdown-selector" href="#">click to upload</a>
</p>
</div>
</div>
<br>
<div class="dropzone-alerts alert alert-danger data" style="display:none"></div>
<div class="form-group row commit_message-group">
<label class="col-form-label col-sm-2" for="commit_message-607b94ccbe344217e3002f00b1d85e42">Commit message
</label><div class="col-sm-10">
<div class="commit-message-container">
<div class="max-width-marker"></div>
<textarea name="commit_message" id="commit_message-607b94ccbe344217e3002f00b1d85e42" class="form-control js-commit-message" placeholder="Replace smdk5250.h" required="required" rows="3">
Replace smdk5250.h</textarea>
</div>
</div>
</div>

<input type="hidden" name="branch_name" id="branch_name" />
<input type="hidden" name="create_merge_request" id="create_merge_request" value="1" />
<input type="hidden" name="original_branch" id="original_branch" value="HEAD" class="js-original-branch" />

<div class="form-actions">
<button name="button" type="button" class="btn btn-success btn-upload-file" id="submit-all"><i aria-hidden="true" data-hidden="true" class="fa fa-spin fa-spinner js-loading-icon hidden"></i>
Replace file
</button><a class="btn btn-cancel" data-dismiss="modal" href="#">Cancel</a>
<div class="inline prepend-left-10">
A new branch will be created in your fork and a new merge request will be started.
</div>

</div>
</form></div>
</div>
</div>
</div>

</div>

</div>
</div>
</div>
</div>




</body>
</html>

