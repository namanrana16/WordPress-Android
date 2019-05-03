package org.wordpress.android.ui.domains

import android.app.ProgressDialog
import android.os.Bundle
import android.support.design.widget.TextInputEditText
import android.support.design.widget.TextInputLayout
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import kotlinx.android.synthetic.main.domain_registration_details_fragment.*
import org.apache.commons.text.StringEscapeUtils
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.R
import org.wordpress.android.WordPress
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.action.TransactionAction.FETCH_SUPPORTED_COUNTRIES
import org.wordpress.android.fluxc.generated.AccountActionBuilder
import org.wordpress.android.fluxc.generated.SiteActionBuilder
import org.wordpress.android.fluxc.generated.TransactionActionBuilder
import org.wordpress.android.fluxc.model.DomainContactModel
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.network.rest.wpcom.site.SupportedStateResponse
import org.wordpress.android.fluxc.network.rest.wpcom.transactions.SupportedDomainCountry
import org.wordpress.android.fluxc.store.AccountStore.OnDomainContactFetched
import org.wordpress.android.fluxc.store.SiteStore
import org.wordpress.android.fluxc.store.SiteStore.OnDomainSupportedStatesFetched
import org.wordpress.android.fluxc.store.SiteStore.OnSiteChanged
import org.wordpress.android.fluxc.store.TransactionsStore
import org.wordpress.android.fluxc.store.TransactionsStore.CreateShoppingCartPayload
import org.wordpress.android.fluxc.store.TransactionsStore.OnShoppingCartCreated
import org.wordpress.android.fluxc.store.TransactionsStore.OnShoppingCartRedeemed
import org.wordpress.android.fluxc.store.TransactionsStore.OnSupportedCountriesFetched
import org.wordpress.android.fluxc.store.TransactionsStore.RedeemShoppingCartPayload
import org.wordpress.android.fluxc.store.TransactionsStore.TransactionErrorType.ADDRESS_1
import org.wordpress.android.fluxc.store.TransactionsStore.TransactionErrorType.ADDRESS_2
import org.wordpress.android.fluxc.store.TransactionsStore.TransactionErrorType.CITY
import org.wordpress.android.fluxc.store.TransactionsStore.TransactionErrorType.COUNTRY_CODE
import org.wordpress.android.fluxc.store.TransactionsStore.TransactionErrorType.EMAIL
import org.wordpress.android.fluxc.store.TransactionsStore.TransactionErrorType.FIRST_NAME
import org.wordpress.android.fluxc.store.TransactionsStore.TransactionErrorType.LAST_NAME
import org.wordpress.android.fluxc.store.TransactionsStore.TransactionErrorType.ORGANIZATION
import org.wordpress.android.fluxc.store.TransactionsStore.TransactionErrorType.PHONE
import org.wordpress.android.fluxc.store.TransactionsStore.TransactionErrorType.POSTAL_CODE
import org.wordpress.android.fluxc.store.TransactionsStore.TransactionErrorType.STATE
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T
import org.wordpress.android.util.StringUtils
import org.wordpress.android.util.ToastUtils
import javax.inject.Inject

class DomainRegistrationDetailsFragment : Fragment() {
    companion object {
        private const val PHONE_NUMBER_PREFIX = "+"
        private const val PHONE_NUMBER_CONNECTING_CHARACTER = "."

        private const val EXTRA_DOMAIN_PRODUCT_DETAILS = "EXTRA_DOMAIN_PRODUCT_DETAILS"
        const val TAG = "DOMAIN_SUGGESTION_FRAGMENT_TAG"

        private const val EXTRA_STATES_FETCH_PROGRESS_VISIBLE = "EXTRA_STATES_FETCH_PROGRESS_VISIBLE"
        private const val EXTRA_PROGRESS_DIALOG_VISIBLE = "EXTRA_PROGRESS_DIALOG_VISIBLE"
        private const val EXTRA_FORM_PROGRESS_INDICATOR_VISIBLE = "EXTRA_FORM_PROGRESS_INDICATOR_VISIBLE"

        private const val EXTRA_SUPPORTED_COUNTRIES = "EXTRA_SUPPORTED_COUNTRIES"
        private const val EXTRA_SUPPORTED_STATES = "EXTRA_SUPPORTED_STATES"

        private const val EXTRA_SELECTED_COUNTRY = "EXTRA_SELECTED_COUNTRY"
        private const val EXTRA_SELECTED_STATE = "EXTRA_SELECTED_STATE"
        private const val EXTRA_INITIAL_DOMAIN_CONTACT_MODEL = "EXTRA_INITIAL_DOMAIN_CONTACT_MODEL"

        fun newInstance(domainProductDetails: DomainProductDetails): DomainRegistrationDetailsFragment {
            val fragment = DomainRegistrationDetailsFragment()
            val bundle = Bundle()
            bundle.putParcelable(EXTRA_DOMAIN_PRODUCT_DETAILS, domainProductDetails)
            fragment.arguments = bundle
            return fragment
        }
    }

    @Inject lateinit var siteStore: SiteStore
    @Inject lateinit var transactionStore: TransactionsStore // needs to be included
    @Inject lateinit var dispatcher: Dispatcher

    private var site: SiteModel? = null
    private var domainProductDetails: DomainProductDetails? = null
    private var loadingProgressDialog: ProgressDialog? = null

    private var supportedCountries: Array<SupportedDomainCountry>? = null
    private var supportedStates: Array<SupportedStateResponse>? = null

    private var selectedCountry: SupportedDomainCountry? = null
    private var selectedState: SupportedStateResponse? = null
    private var initialDomainContactModel: DomainContactModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nonNullActivity = checkNotNull(activity)
        (nonNullActivity.application as WordPress).component()?.inject(this)

        site = activity?.intent?.getSerializableExtra(WordPress.SITE) as SiteModel

        domainProductDetails = if (savedInstanceState != null) {
            savedInstanceState.getParcelable(EXTRA_DOMAIN_PRODUCT_DETAILS)
        } else {
            arguments?.getParcelable(EXTRA_DOMAIN_PRODUCT_DETAILS)
        }

        if (savedInstanceState != null) {
            supportedCountries = savedInstanceState.getParcelableArray(EXTRA_SUPPORTED_COUNTRIES)
                    as Array<SupportedDomainCountry>?
            supportedStates = savedInstanceState.getParcelableArray(EXTRA_SUPPORTED_STATES)
                    as Array<SupportedStateResponse>?

            selectedCountry = savedInstanceState.getParcelable(EXTRA_SELECTED_COUNTRY)
            selectedState = savedInstanceState.getParcelable(EXTRA_SELECTED_STATE)

            initialDomainContactModel = savedInstanceState.getParcelable(EXTRA_INITIAL_DOMAIN_CONTACT_MODEL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(WordPress.SITE, site)
        outState.putParcelable(EXTRA_DOMAIN_PRODUCT_DETAILS, domainProductDetails)
        outState.putParcelableArray(EXTRA_SUPPORTED_COUNTRIES, supportedCountries)
        outState.putParcelableArray(EXTRA_SUPPORTED_STATES, supportedStates)
        outState.putParcelable(EXTRA_SELECTED_COUNTRY, selectedCountry)
        outState.putParcelable(EXTRA_SELECTED_STATE, selectedState)
        outState.putParcelable(EXTRA_INITIAL_DOMAIN_CONTACT_MODEL, initialDomainContactModel)
        outState.putBoolean(
                EXTRA_PROGRESS_DIALOG_VISIBLE,
                loadingProgressDialog != null && loadingProgressDialog!!.isShowing
        )

        outState.putBoolean(
                EXTRA_STATES_FETCH_PROGRESS_VISIBLE,
                states_loading_progress_indicator.visibility == View.VISIBLE
        )
        outState.putBoolean(EXTRA_FORM_PROGRESS_INDICATOR_VISIBLE, form_progress_indicator.visibility == View.VISIBLE)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.domain_registration_details_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkNotNull((activity?.application as WordPress).component())
        (activity?.application as WordPress).component().inject(this)

        country_input.inputType = 0
        country_input.setOnClickListener {
            showCountryPicker(supportedCountries!!)
        }

        state_input.inputType = 0
        state_input.setOnClickListener {
            showStatePicker(supportedStates!!)
        }

        register_domain_button.setOnClickListener {
            if (validateForm()) {
                registerDomain()
            }
        }

        if (supportedStates == null || supportedStates!!.isEmpty()) {
            state_input.isClickable = false
            state_input.isFocusable = false
        }

        if (savedInstanceState != null) {
            val isStatesLoadingProgressVisible = savedInstanceState.getBoolean(EXTRA_STATES_FETCH_PROGRESS_VISIBLE)
            val isFormProgressIndicatorVisible = savedInstanceState.getBoolean(EXTRA_FORM_PROGRESS_INDICATOR_VISIBLE)
            val isProgressDialogVisible = savedInstanceState.getBoolean(EXTRA_PROGRESS_DIALOG_VISIBLE)

            if (isFormProgressIndicatorVisible) {
                showFormProgressIndicator()
            } else {
                hideFormProgressIndicator()
            }

            if (isStatesLoadingProgressVisible) {
                showStatesProgressIndicator()
            } else {
                hideStatesProgressIndicator()
            }

            if (isProgressDialogVisible) {
                showProgressDialog()
            }
        } else if (supportedCountries == null || supportedCountries!!.isEmpty()) {
            showFormProgressIndicator()
            fetchInitialData()
        }
    }

    private fun populateContactForm(domainContactInformation: DomainContactModel) {
        first_name_input.setText(domainContactInformation.firstName)
        last_name_input.setText(domainContactInformation.lastName)
        organization_input.setText(domainContactInformation.organization)
        email_input.setText(domainContactInformation.email)

        country_input.setText(selectedCountry?.name)
        state_input.setText(domainContactInformation.state)

        val phoneParts = domainContactInformation.phone!!.split(".")
        var countryCode = phoneParts[0]
        if (countryCode.startsWith("+")) {
            countryCode = countryCode.drop(1)
        }

        val phoneNumber = phoneParts[1]

        country_code_input.setText(countryCode)
        phone_number_input.setText(phoneNumber)
        address_first_line_input.setText(domainContactInformation.addressLine1)
        address_second_line_input.setText(domainContactInformation.addressLine2)
        city_input.setText(domainContactInformation.city)
        postal_code_input.setText(domainContactInformation.postalCode)
    }

    private fun validateForm(): Boolean {
        var formIsCompleted = true

        val requiredFields = arrayOf(
                first_name_input, last_name_input, email_input, country_code_input, phone_number_input,
                country_input, address_first_line_input, city_input, postal_code_input
        )

        requiredFields.forEach {
            if (TextUtils.isEmpty(it.text)) {
                showEmptyFieldError(it)
                if (formIsCompleted) {
                    formIsCompleted = false
                }
            }
        }

        return formIsCompleted
    }

    private fun showEmptyFieldError(editText: EditText) {
        val parent = editText.parent.parent
        if (parent is TextInputLayout) {
            showFieldError(editText, getString(R.string.domain_registration_contact_form_input_error, parent.hint))
        }
    }

    private fun showFieldError(editText: EditText, errorMessage: String) {
        editText.error = errorMessage
    }

    private fun registerDomain() {
        showProgressDialog()
        dispatcher.dispatch(
                TransactionActionBuilder.newCreateShoppingCartAction(
                        CreateShoppingCartPayload(
                                site!!,
                                domainProductDetails!!.productId,
                                domainProductDetails!!.domainName,
                                domain_privacy_on_radio_button.isChecked
                        )
                )
        )
    }

    private fun contactFormToDomainContactModel(): DomainContactModel {
        val combinedPhoneNumber = getString(
                R.string.domain_registration_phone_number_format,
                country_code_input.text,
                phone_number_input.text
        )

        return DomainContactModel(
                first_name_input.text.toString(),
                last_name_input.text.toString(),
                StringUtils.notNullStr(organization_input.text.toString()),
                address_first_line_input.text.toString(),
                address_second_line_input.text.toString(),
                postal_code_input.text.toString(),
                city_input.text.toString(),
                selectedState?.code,
                selectedCountry?.code,
                email_input.text.toString(),
                combinedPhoneNumber,
                null
        )
    }

    private fun showStatePicker(states: Array<SupportedStateResponse>) {
        if (states.isEmpty()) {
            return
        }

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.domain_registration_state_picker_dialog_title)
        builder.setItems(states.map { it.name }.toTypedArray()) { _, which ->
            selectedState = states[which]
            state_input.setText(selectedState!!.name)
        }

        builder.setPositiveButton(R.string.dialog_button_cancel) { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    private fun showCountryPicker(countries: Array<SupportedDomainCountry>) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.domain_registration_country_picker_dialog_title)
        builder.setItems(countries.map { it.name }.toTypedArray()) { _, which ->
            val pickedCountry = countries[which]

            if (selectedCountry != pickedCountry) {
                selectedCountry = pickedCountry
                country_input.setText(selectedCountry!!.name)
                selectedState = null
                state_input.text = null
                fetchStates()
            }
        }

        builder.setPositiveButton(R.string.dialog_button_cancel) { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    private fun showFormProgressIndicator() {
        form_progress_indicator.visibility = View.VISIBLE
    }

    private fun hideFormProgressIndicator() {
        form_progress_indicator.visibility = View.GONE
    }

    private fun showStatesProgressIndicator() {
        states_loading_progress_indicator.visibility = View.VISIBLE
    }

    private fun hideStatesProgressIndicator() {
        states_loading_progress_indicator.visibility = View.GONE
    }

    private fun showProgressDialog() {
        if (loadingProgressDialog == null) {
            loadingProgressDialog = ProgressDialog(context)
            loadingProgressDialog!!.isIndeterminate = true
            loadingProgressDialog!!.setCancelable(false)

            loadingProgressDialog!!
                    .setMessage(getString(R.string.loading))
        }
        if (!loadingProgressDialog!!.isShowing) {
            loadingProgressDialog!!.show()
        }
    }

    private fun cancelProgressDialog() {
        if (loadingProgressDialog != null && loadingProgressDialog!!.isShowing) {
            loadingProgressDialog!!.cancel()
        }
    }

    private fun fetchContactInformation() {
        dispatcher.dispatch(AccountActionBuilder.newFetchDomainContactAction())
    }

    private fun fetchInitialData() {
        dispatcher.dispatch(TransactionActionBuilder.generateNoPayloadAction(FETCH_SUPPORTED_COUNTRIES))
    }

    private fun fetchStates() {
        states_loading_progress_indicator.visibility = View.VISIBLE
        state_input.isEnabled = false
        state_input.isClickable = false
        dispatcher.dispatch(SiteActionBuilder.newFetchDomainSupportedStatesAction(selectedCountry!!.code))
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSupportedCountriesFetched(event: OnSupportedCountriesFetched) {
        if (event.isError) {
            hideFormProgressIndicator()
            AppLog.e(
                    T.DOMAIN_REGISTRATION,
                    "An error occurred while fetching supported countries : " + event.error.message
            )
            ToastUtils.showToast(
                    context,
                    getString(R.string.domain_registration_error_fetching_contact_information, event.error.message)
            )
            return
        }
        supportedCountries = event.countries
        fetchContactInformation()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDomainContactFetched(event: OnDomainContactFetched) {
        if (event.isError) {
            hideFormProgressIndicator()
            AppLog.e(
                    T.DOMAIN_REGISTRATION,
                    "An error occurred while fetching domain contact information : " + event.error.message
            )
            ToastUtils.showToast(
                    context,
                    getString(R.string.domain_registration_error_fetching_contact_information, event.error.message)
            )
        } else {
            initialDomainContactModel = event.contactModel
            if (initialDomainContactModel != null && !TextUtils.isEmpty(initialDomainContactModel!!.countryCode)) {
                selectedCountry = supportedCountries!!.firstOrNull { it.code == event.contactModel!!.countryCode }
                populateContactForm(initialDomainContactModel!!)
                fetchStates()
            } else {
                hideFormProgressIndicator()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDomainSupportedStatesFetched(event: OnDomainSupportedStatesFetched) {
        hideFormProgressIndicator()
        states_loading_progress_indicator.visibility = View.GONE
        if (event.isError) {
            AppLog.e(
                    T.DOMAIN_REGISTRATION,
                    "An error occurred while fetching supported states : " + event.error.message
            )
            ToastUtils.showToast(
                    context,
                    getString(R.string.domain_registration_error_fetching_contact_information, event.error.message)
            )
            return
        }
        supportedStates = event.supportedStates?.toTypedArray()
        if (supportedStates != null && supportedStates!!.isNotEmpty()) {
            state_input.isEnabled = true
            state_input.isClickable = true

            selectedState = supportedStates?.firstOrNull { it.code == initialDomainContactModel?.state }
            state_input.setText(selectedState?.name)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onShoppingCartCreated(event: OnShoppingCartCreated) {
        if (event.isError) {
            cancelProgressDialog()
            AppLog.e(
                    T.DOMAIN_REGISTRATION,
                    "An error occurred while creating a shopping cart : " + event.error.message
            )
            ToastUtils.showToast(
                    context,
                    getString(R.string.domain_registration_error_registering_domain, event.error.message)
            )
            return
        }

        val domainContactInformation = contactFormToDomainContactModel()
        dispatcher.dispatch(
                TransactionActionBuilder.newRedeemCartWithCreditsAction(
                        RedeemShoppingCartPayload(
                                event.cartDetails!!, domainContactInformation
                        )
                )
        )
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onCartRedeemed(event: OnShoppingCartRedeemed) {
        if (event.isError) {
            cancelProgressDialog()
            AppLog.e(
                    T.DOMAIN_REGISTRATION,
                    "An error occurred while redeeming a shopping cart : " + event.error.type +
                            " " + event.error.message
            )
            ToastUtils.showToast(
                    context,
                    getString(R.string.domain_registration_error_registering_domain, event.error.message)
            )
            var affectedInputFields: Array<TextInputEditText>? = null

            when (event.error.type) {
                FIRST_NAME -> {
                    affectedInputFields = arrayOf(first_name_input)
                }
                LAST_NAME -> affectedInputFields = arrayOf(last_name_input)
                ORGANIZATION -> affectedInputFields = arrayOf(organization_input)
                ADDRESS_1 -> affectedInputFields = arrayOf(address_first_line_input)
                ADDRESS_2 -> affectedInputFields = arrayOf(address_second_line_input)
                POSTAL_CODE -> affectedInputFields = arrayOf(postal_code_input)
                CITY -> affectedInputFields = arrayOf(city_input)
                STATE -> affectedInputFields = arrayOf(state_input)
                COUNTRY_CODE -> affectedInputFields = arrayOf(country_code_input)
                EMAIL -> affectedInputFields = arrayOf(email_input)
                PHONE -> affectedInputFields = arrayOf(country_code_input, phone_number_input)
                else -> {
                } // Something else, will just show a Toast with an error message
            }
            affectedInputFields?.forEach { showFieldError(it, StringEscapeUtils.unescapeHtml4(event.error.message)) }
            affectedInputFields?.first { it.requestFocus() }
            return
        }

        dispatcher.dispatch(SiteActionBuilder.newFetchSiteAction(site))
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSiteChanged(event: OnSiteChanged) {
        cancelProgressDialog()
        if (event.isError) {
            AppLog.e(
                    T.DOMAIN_REGISTRATION,
                    "An error occurred while updating site details : " + event.error.message
            )
            ToastUtils.showToast(
                    context,
                    getString(R.string.domain_registration_error_updating_site, event.error.message)
            )
        }
        (activity as DomainRegistrationActivity).onDomainRegistered(domainProductDetails!!.domainName)
    }

    override fun onStart() {
        super.onStart()
        dispatcher.register(this)
    }

    override fun onStop() {
        super.onStop()
        dispatcher.unregister(this)
    }
}
