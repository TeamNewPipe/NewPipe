# Android Kotlin Code Review Guidelines

## Architecture & Design
- **Clean Architecture**: Ensure a strict separation between Presentation, Domain, and Data layers.
- **MVVM/MVI**: Flag any business logic found inside Activities, Fragments, or Composable functions. It should reside in ViewModels or UseCases.
- **Dependency Injection**: Verify all dependencies are provided via Hilt/Dagger constructors. Flag manual instantiation of repositories or services.

## Jetpack Compose (UI)
- **State Management**: Prefer `rememberSaveable` for simple state and ensure heavy logic is moved to `ViewModel`.
- **Performance**: Flag large composables that could be broken down to minimize recomposition.
- **Lazy Lists**: Ensure `LazyColumn` or `LazyRow` is used for scrollable content instead of standard Columns with scrolling modifiers.
- **Preview Support**: Every new Composable should include at least one `@Preview` function.

## Kotlin & Coroutines
- **Structured Concurrency**: Flag the use of `GlobalScope`. Always prefer `viewModelScope` or `lifecycleScope`.
- **Dispatchers**: Ensure network or database calls use `Dispatchers.IO`. CPU-intensive tasks should use `Dispatchers.Default`.
- **Safety**: Check for proper null safety usage. Flag the use of `!!` (not-null assertion) unless absolutely justified.
- **Immutability**: Prefer `val` over `var` and immutable collections (e.g., `List` over `MutableList`) where possible.

## Error Handling & Security
- **Explicit Exceptions**: Do not catch generic `Exception` or `Throwable`. Catch specific errors (e.g., `IOException`).
- **Secrets**: Flag hardcoded API keys or sensitive strings. Check that they are managed via `BuildConfig` or secret managers.

## Testing
- **Coverage**: Every new Feature or UseCase must have a corresponding unit test file.
- **Compose Testing**: UI elements should include `Modifier.testTag` for easier automated testing.
