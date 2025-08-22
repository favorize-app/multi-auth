# Cursor Development Instructions for Multi-Auth System

## 🎯 **CRITICAL: Follow These Instructions Exactly**

This document provides step-by-step instructions for Cursor to continue developing the Multi-Auth system according to the development plan.

## 📋 **Current Status**

**Phase 5: gRPC Integration & Testing** ✅ **COMPLETED**
**Phase 6: Compose UI Components** 🔄 **IN PROGRESS**

## 🚀 **What to Do Next: Phase 6 - Compose UI Components**

### **Step 1: Authentication Screen Components**

Create the following Compose UI components in `composeApp/src/commonMain/kotlin/app/multiauth/ui/`:

#### 1.1 Login Screen (`LoginScreen.kt`)
- Email and password input fields
- Form validation (email format, password requirements)
- Login button with loading state
- Error message display
- "Forgot Password" link
- "Register" link
- Integration with AuthEngine.signInWithEmail()

#### 1.2 Register Screen (`RegisterScreen.kt`)
- Email, password, and display name input fields
- Password confirmation field
- Form validation (email format, password strength, matching passwords)
- Register button with loading state
- Error message display
- "Already have account" link
- Integration with AuthEngine.registerWithEmail()

#### 1.3 Forgot Password Screen (`ForgotPasswordScreen.kt`)
- Email input field
- Form validation (email format)
- Submit button with loading state
- Success/error message display
- "Back to Login" link
- Integration with EmailProvider.sendPasswordResetEmail()

### **Step 2: OAuth Flow UI Components**

#### 2.1 OAuth Provider Selection (`OAuthProviderSelection.kt`)
- List of available OAuth providers (Google, Apple, Facebook, etc.)
- Provider icons and names
- Selection handling
- Integration with OAuthManager.signInWithOAuth()

#### 2.2 OAuth Flow Progress (`OAuthFlowProgress.kt`)
- Progress indicators for OAuth flow stages
- Loading states and animations
- Error handling and user feedback
- Integration with OAuthManager.oauthState

#### 2.3 OAuth Callback Handler (`OAuthCallbackHandler.kt`)
- Handle OAuth callback URLs
- Process authorization codes
- Error handling for OAuth failures
- Integration with platform-specific OAuth implementations

### **Step 3: Biometric Authentication UI**

#### 3.1 Biometric Prompt (`BiometricPrompt.kt`)
- Biometric authentication interface
- Fingerprint/face recognition prompts
- Fallback options (PIN, password)
- Error handling and user guidance
- Integration with BiometricManager.authenticateWithBiometric()

#### 3.2 Biometric Setup (`BiometricSetup.kt`)
- Enable/disable biometric authentication
- Biometric type selection
- Setup wizard and instructions
- Integration with BiometricManager.enableBiometric()

#### 3.3 Biometric Settings (`BiometricSettings.kt`)
- Biometric configuration options
- Security settings and preferences
- Account linking/unlinking
- Integration with BiometricManager

### **Step 4: Profile and Settings UI**

#### 4.1 User Profile (`UserProfile.kt`)
- Display user information (name, email, profile picture)
- Edit profile functionality
- Password change interface
- Account deletion options
- Integration with UserManagementService

#### 4.2 Settings Screen (`SettingsScreen.kt`)
- General app settings
- Authentication preferences
- Privacy and security settings
- Notification preferences
- Integration with various managers

#### 4.3 Account Linking (`AccountLinking.kt`)
- Link/unlink OAuth providers
- Manage connected accounts
- Security verification for changes
- Integration with OAuthManager

## 🔧 **Technical Requirements**

### **Architecture Guidelines**
- Use Compose Multiplatform for cross-platform UI
- Follow MVVM pattern with ViewModels
- Use StateFlow for reactive UI updates
- Implement proper error handling and user feedback
- Use the existing event system for communication

### **UI/UX Guidelines**
- Follow Material Design principles
- Implement responsive design for different screen sizes
- Use consistent theming and styling
- Provide clear error messages and user guidance
- Implement proper loading states and animations

### **Integration Requirements**
- All UI components must integrate with existing managers
- Use the event system for state updates
- Implement proper error handling using existing error types
- Follow the established patterns in the codebase

## 📁 **File Structure to Create**

```
composeApp/src/commonMain/kotlin/app/multiauth/ui/
├── auth/
│   ├── LoginScreen.kt
│   ├── RegisterScreen.kt
│   └── ForgotPasswordScreen.kt
├── oauth/
│   ├── OAuthProviderSelection.kt
│   ├── OAuthFlowProgress.kt
│   └── OAuthCallbackHandler.kt
├── biometric/
│   ├── BiometricPrompt.kt
│   ├── BiometricSetup.kt
│   └── BiometricSettings.kt
├── profile/
│   ├── UserProfile.kt
│   ├── SettingsScreen.kt
│   └── AccountLinking.kt
├── common/
│   ├── LoadingSpinner.kt
│   ├── ErrorMessage.kt
│   ├── FormField.kt
│   └── Button.kt
└── theme/
    ├── Colors.kt
    ├── Typography.kt
    └── Theme.kt
```

## ✅ **Completion Checklist**

Before marking Phase 6 as complete, ensure:

- [ ] All authentication screens are implemented and functional
- [ ] OAuth flow UI components are complete
- [ ] Biometric authentication UI is implemented
- [ ] Profile and settings UI is functional
- [ ] All components integrate with existing managers
- [ ] Proper error handling is implemented
- [ ] UI is responsive and follows design guidelines
- [ ] Components are properly tested
- [ ] Code follows established patterns

## 🚫 **What NOT to Do**

- Don't skip any of the required components
- Don't implement UI without proper integration
- Don't ignore error handling requirements
- Don't create UI that doesn't follow the established patterns
- Don't mark items as complete without testing
- Don't move to Phase 7 until Phase 6 is 100% complete

## 📝 **Progress Tracking**

After completing each component:
1. Update the development plan with checkmarks
2. Commit the code with descriptive messages
3. Test the component thoroughly
4. Update this instruction document if needed

## 🔄 **Next Phase Preparation**

Once Phase 6 is complete, the system will be ready for:
- **Phase 7**: Advanced Features & Polish
- Multi-factor authentication (MFA)
- Social login integration
- Account linking
- Security audit and penetration testing

## 📚 **Reference Materials**

- **Development Plan**: `docs/MULTI_AUTH_DEVELOPMENT_PLAN.md`
- **Architecture**: `docs/ARCHITECTURE.md`
- **API Reference**: `docs/API_REFERENCE.md`
- **Existing Code**: Check the `shared/` module for patterns

## 🆘 **Need Help?**

If you encounter issues or need clarification:
1. Check the existing codebase for patterns
2. Review the development plan for context
3. Ensure all dependencies are properly imported
4. Follow the established error handling patterns
5. Use the existing event system for communication

---

**Remember**: Follow the development plan exactly, complete one phase at a time, and maintain the established code quality standards.