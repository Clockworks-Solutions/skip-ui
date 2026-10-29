// Copyright 2026 Skip
// SPDX-License-Identifier: MPL-2.0
import SwiftUI
import XCTest

#if SKIP
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
#endif

/// Verifies the `isPresented` and `isFocused` environment values on Android.
final class PresentationFocusEnvironmentTests: SkipUITestCase {
    // SKIP INSERT: @get:org.junit.Rule val composeRule = createComposeRule()

    func testIsPresentedIsFalseOutsidePresentation() throws {
        #if !SKIP
        throw XCTSkip("Android-only")
        #else
        composeRule.setContent {
            PresentedReader(tag: "root").Compose()
        }
        composeRule.onNodeWithTag("root").assertTextEquals("presented: false")
        #endif
    }

    func testIsPresentedInSheet() throws {
        #if !SKIP
        throw XCTSkip("Android-only")
        #else
        composeRule.setContent {
            SheetHost().Compose()
        }
        composeRule.onNodeWithTag("presenter").assertTextEquals("presented: false")

        composeRule.onNodeWithTag("show").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sheet").assertTextEquals("presented: true")
        // Presenting content is unaffected by the sheet's environment
        composeRule.onNodeWithTag("presenter").assertTextEquals("presented: false")
        #endif
    }

    func testIsPresentedInPushedDestination() throws {
        #if !SKIP
        throw XCTSkip("Android-only")
        #else
        composeRule.setContent {
            NavigationHost().Compose()
        }
        composeRule.onNodeWithTag("stack.root").assertTextEquals("presented: false")

        composeRule.onNodeWithTag("push").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("destination").assertTextEquals("presented: true")
        #endif
    }

    func testFocusableUpdatesIsFocused() throws {
        #if !SKIP
        throw XCTSkip("Android-only")
        #else
        var inputModeManager: InputModeManager? = nil
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            FocusableHost().Compose()
        }
        composeRule.onNodeWithTag("focusable.reader").assertTextEquals("focused: false")
        composeRule.onNodeWithTag("sibling.reader").assertTextEquals("focused: false")

        composeRule.runOnIdle { inputModeManager?.requestInputMode(InputMode.Keyboard) }
        composeRule.onNodeWithTag("focusable").requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("focusable.reader").assertTextEquals("focused: true")
        // A sibling focusable view keeps its own focus state
        composeRule.onNodeWithTag("sibling.reader").assertTextEquals("focused: false")
        #endif
    }

    func testFocusableIsNotFocusableInTouchMode() throws {
        #if !SKIP
        throw XCTSkip("Android-only")
        #else
        composeRule.setContent {
            FocusableHost().Compose()
        }

        // Like iOS, `.focusable()` views only take focus during keyboard navigation
        composeRule.onNodeWithTag("focusable").requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("focusable.reader").assertTextEquals("focused: false")
        #endif
    }

    func testFocusableIgnoresFocusedDescendant() throws {
        #if !SKIP
        throw XCTSkip("Android-only")
        #else
        composeRule.setContent {
            FocusStateHost().Compose()
        }

        composeRule.onNodeWithTag("container.focus").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("container.field").assertIsFocused()
        // `isFocused` reflects the focusable view itself, as on iOS
        composeRule.onNodeWithTag("container.reader").assertTextEquals("focused: false")
        #endif
    }

    /// Regression check for skiptools/skip-ui#552: setting a `@FocusState` to `false` clears native focus.
    func testFocusStateFalseClearsTextFieldFocus() throws {
        #if !SKIP
        throw XCTSkip("Android-only")
        #else
        composeRule.setContent {
            FocusStateHost().Compose()
        }

        composeRule.onNodeWithTag("plain.focus").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("plain.field").assertIsFocused()

        composeRule.onNodeWithTag("plain.unfocus").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("plain.field").assertIsNotFocused()
        #endif
    }

    /// Regression check for skiptools/skip-ui#552 when the text field sits inside a `.focusable()` view.
    func testFocusStateNilClearsTextFieldFocusInsideFocusable() throws {
        #if !SKIP
        throw XCTSkip("Android-only")
        #else
        composeRule.setContent {
            FocusStateHost().Compose()
        }

        composeRule.onNodeWithTag("container.focus").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("container.field").assertIsFocused()

        composeRule.onNodeWithTag("container.unfocus").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("container.field").assertIsNotFocused()
        #endif
    }
}

enum FocusStateField: Hashable {
    case field
}

struct FocusStateHost: View {
    @FocusState var isPlainFocused: Bool
    @FocusState var containerField: FocusStateField?
    @State var plainText = ""
    @State var containerText = ""

    var body: some View {
        VStack {
            Button("Focus") { isPlainFocused = true }
                .accessibilityIdentifier("plain.focus")
            Button("Unfocus") { isPlainFocused = false }
                .accessibilityIdentifier("plain.unfocus")
            TextField("Plain", text: $plainText)
                .focused($isPlainFocused)
                .accessibilityIdentifier("plain.field")

            Button("Focus") { containerField = .field }
                .accessibilityIdentifier("container.focus")
            Button("Unfocus") { containerField = nil }
                .accessibilityIdentifier("container.unfocus")
            VStack {
                FocusedReader(tag: "container.reader")
                TextField("In container", text: $containerText)
                    .focused($containerField, equals: .field)
                    .accessibilityIdentifier("container.field")
            }
            .focusable()
        }
    }
}

struct PresentedReader: View {
    let tag: String
    @Environment(\.isPresented) var isPresented

    var body: some View {
        Text("presented: \(isPresented)")
            .accessibilityIdentifier(tag)
    }
}

struct FocusedReader: View {
    let tag: String
    @Environment(\.isFocused) var isFocused

    var body: some View {
        Text("focused: \(isFocused)")
            .accessibilityIdentifier(tag)
    }
}

struct SheetHost: View {
    @State var isShowingSheet = false

    var body: some View {
        VStack {
            PresentedReader(tag: "presenter")
            Button("Show") { isShowingSheet = true }
                .accessibilityIdentifier("show")
        }
        .sheet(isPresented: $isShowingSheet) {
            PresentedReader(tag: "sheet")
        }
    }
}

struct NavigationHost: View {
    var body: some View {
        NavigationStack {
            VStack {
                PresentedReader(tag: "stack.root")
                NavigationLink("Push") {
                    PresentedReader(tag: "destination")
                }
                .accessibilityIdentifier("push")
            }
        }
    }
}

struct FocusableHost: View {
    var body: some View {
        VStack {
            VStack {
                Text("Focus target")
                FocusedReader(tag: "focusable.reader")
            }
            .focusable()
            .accessibilityIdentifier("focusable")
            FocusedReader(tag: "sibling.reader")
                .focusable()
        }
    }
}
